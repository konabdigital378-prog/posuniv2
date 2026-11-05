package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Real hardware camera barcode scanner component using CameraX and Google ML Kit.
 * Features:
 * - Real CameraX live hardware preview
 * - ML Kit on-device barcode parsing (EAN-13, EAN-8, QR Code, Code 128, UPC, etc.)
 * - Runtime camera permission check & graceful fallback
 * - Flashlight/Torch toggle
 * - Laser reticle animation & corner guides
 * - Sound and vibration feedback upon scan
 * - Continuous scanning mode or single scan mode
 * - Manual input fallback
 */
@Composable
fun RealBarcodeScannerModal(
    title: String = "Scanner de code-barres",
    subtitle: String = "Visez le code-barres de l'article pour le scanner",
    continuousScan: Boolean = false,
    lastScannedBarcode: String? = null,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var scanSuccessFeedback by remember { mutableStateOf<String?>(null) }

    // Trigger permission request on start if not yet granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Live CameraX Preview with ML Kit Analyzer
                CameraScannerPreview(
                    modifier = Modifier.fillMaxSize(),
                    isTorchEnabled = isTorchEnabled,
                    onCameraControlReady = { cameraControl = it },
                    onBarcodeDetected = { barcode ->
                        scanSuccessFeedback = barcode
                        playScanFeedback(context)
                        onBarcodeScanned(barcode)
                    }
                )

                // Dark Scrim Overlay with Viewfinder Reticle
                ViewfinderOverlay(
                    modifier = Modifier.fillMaxSize(),
                    successFeedback = scanSuccessFeedback
                )
            } else {
                // Fallback UI when Camera permission is denied
                CameraPermissionDeniedFallback(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onManualSubmit = { code ->
                        if (code.isNotBlank()) {
                            playScanFeedback(context)
                            onBarcodeScanned(code.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            }

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_close_scanner")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fermer le scanner",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (continuousScan) "Mode continu actif" else "Scanner matériel actif",
                        color = KonabGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (hasCameraPermission) {
                    IconButton(
                        onClick = {
                            val targetState = !isTorchEnabled
                            isTorchEnabled = targetState
                            cameraControl?.enableTorch(targetState)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isTorchEnabled) KonabGold else Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .testTag("btn_toggle_torch")
                    ) {
                        Icon(
                            if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Lampe torche",
                            tint = if (isTorchEnabled) Color.Black else Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }

            // Bottom Control and Status Area
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Success feedback banner
                if (scanSuccessFeedback != null || lastScannedBarcode != null) {
                    Surface(
                        color = KonabGreenDark.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = KonabGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Code : ${scanSuccessFeedback ?: lastScannedBarcode}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Subtitle help text
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Manual Input Button
                OutlinedButton(
                    onClick = { showManualInput = !showManualInput },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        containerColor = Color.Black.copy(alpha = 0.6f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(KonabGold)
                    ),
                    modifier = Modifier.testTag("btn_manual_barcode_toggle")
                ) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = KonabGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showManualInput) "Masquer saisie manuelle" else "Saisir le code manuellement")
                }

                // Manual Input Panel
                if (showManualInput) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualBarcode,
                                onValueChange = { manualBarcode = it },
                                placeholder = { Text("Ex: 618110001234", fontSize = 13.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (manualBarcode.isNotBlank()) {
                                            playScanFeedback(context)
                                            onBarcodeScanned(manualBarcode.trim())
                                            manualBarcode = ""
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_manual_barcode")
                            )

                            Button(
                                onClick = {
                                    if (manualBarcode.isNotBlank()) {
                                        playScanFeedback(context)
                                        onBarcodeScanned(manualBarcode.trim())
                                        manualBarcode = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                modifier = Modifier.testTag("btn_submit_manual_barcode")
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * CameraX Preview View + ML Kit Barcode Analyzer
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraScannerPreview(
    modifier: Modifier = Modifier,
    isTorchEnabled: Boolean,
    onCameraControlReady: (CameraControl) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
    }
    val barcodeScanner = remember { BarcodeScanning.getClient(scannerOptions) }

    // Debounce barcode detections (ignore duplicates within 1.2 seconds)
    var lastDetectedCode by remember { mutableStateOf<String?>(null) }
    var lastDetectionTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val rawValue = barcode.rawValue ?: barcode.displayValue
                                    if (!rawValue.isNullOrBlank()) {
                                        val now = System.currentTimeMillis()
                                        if (rawValue != lastDetectedCode || now - lastDetectionTime > 1200L) {
                                            lastDetectedCode = rawValue
                                            lastDetectionTime = now
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onBarcodeDetected(rawValue)
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("BarcodeScanner", "Failed to analyze frame", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraControlReady(camera.cameraControl)
                    camera.cameraControl.enableTorch(isTorchEnabled)
                } catch (e: Exception) {
                    Log.e("BarcodeScanner", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

/**
 * Viewfinder overlay with animated laser beam and targeting corners
 */
@Composable
private fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    successFeedback: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    val reticleColor = if (successFeedback != null) KonabGreen else KonabGold

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Reticle box dimensions
        val boxWidth = canvasWidth * 0.76f
        val boxHeight = boxWidth * 0.65f
        val boxLeft = (canvasWidth - boxWidth) / 2f
        val boxTop = (canvasHeight - boxHeight) / 2.3f
        val boxRight = boxLeft + boxWidth
        val boxBottom = boxTop + boxHeight
        val cornerRadius = 16.dp.toPx()

        // 1. Draw darkened overlay with transparent hole
        val overlayPath = Path().apply {
            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            addRoundRect(
                RoundRect(
                    left = boxLeft,
                    top = boxTop,
                    right = boxRight,
                    bottom = boxBottom,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }
        drawPath(
            path = overlayPath,
            color = Color.Black.copy(alpha = 0.58f)
        )

        // 2. Draw 4 Corner Guide Brackets
        val cornerLength = 32.dp.toPx()
        val strokeWidth = 4.dp.toPx()

        // Top Left
        drawLine(
            color = reticleColor,
            start = Offset(boxLeft, boxTop + cornerLength),
            end = Offset(boxLeft, boxTop + cornerRadius),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(boxLeft + cornerRadius, boxTop),
            end = Offset(boxLeft + cornerLength, boxTop),
            strokeWidth = strokeWidth
        )

        // Top Right
        drawLine(
            color = reticleColor,
            start = Offset(boxRight, boxTop + cornerLength),
            end = Offset(boxRight, boxTop + cornerRadius),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(boxRight - cornerRadius, boxTop),
            end = Offset(boxRight - cornerLength, boxTop),
            strokeWidth = strokeWidth
        )

        // Bottom Left
        drawLine(
            color = reticleColor,
            start = Offset(boxLeft, boxBottom - cornerLength),
            end = Offset(boxLeft, boxBottom - cornerRadius),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(boxLeft + cornerRadius, boxBottom),
            end = Offset(boxLeft + cornerLength, boxBottom),
            strokeWidth = strokeWidth
        )

        // Bottom Right
        drawLine(
            color = reticleColor,
            start = Offset(boxRight, boxBottom - cornerLength),
            end = Offset(boxRight, boxBottom - cornerRadius),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(boxRight - cornerRadius, boxBottom),
            end = Offset(boxRight - cornerLength, boxBottom),
            strokeWidth = strokeWidth
        )

        // 3. Draw Laser Scanline
        val laserY = boxTop + (boxHeight * laserProgress)
        drawLine(
            color = if (successFeedback != null) KonabGreen else KonabRed,
            start = Offset(boxLeft + 12.dp.toPx(), laserY),
            end = Offset(boxRight - 12.dp.toPx(), laserY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

/**
 * Fallback UI when Camera permission is denied
 */
@Composable
private fun CameraPermissionDeniedFallback(
    onRequestPermission: () -> Unit,
    onManualSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fallbackBarcode by remember { mutableStateOf("") }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(KonabGoldContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = KonabGoldDark,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Accès caméra requis",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Pour scanner automatiquement les codes-barres de vos produits à la caisse, Konab POS a besoin d'accéder à l'appareil photo.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_request_camera_permission")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Autoriser la caméra")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Ou saisissez le code-barres manuellement :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fallbackBarcode,
                        onValueChange = { fallbackBarcode = it },
                        placeholder = { Text("Code-barres", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (fallbackBarcode.isNotBlank()) {
                                onManualSubmit(fallbackBarcode.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreenDark)
                    ) {
                        Text("Valider")
                    }
                }
            }
        }
    }
}

/**
 * Audio and haptic feedback on successful barcode recognition
 */
private fun playScanFeedback(context: Context) {
    try {
        // Haptic feedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80L)
            }
        }

        // Audio beep feedback
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    } catch (e: Exception) {
        Log.w("BarcodeFeedback", "Feedback error", e)
    }
}
