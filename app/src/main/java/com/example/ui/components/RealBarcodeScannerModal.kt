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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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
import com.example.ui.theme.KonabGold
import com.example.ui.theme.KonabGoldContainer
import com.example.ui.theme.KonabGoldDark
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabGreenDark
import com.example.ui.theme.KonabRed
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real hardware camera barcode & QR scanner component using CameraX and Google ML Kit.
 * Features:
 * - TextureView (ImplementationMode.COMPATIBLE) so preview renders reliably inside Compose Dialogs
 * - Automatic camera selector fallback (back -> front) & graceful error recovery
 * - Clean unbindAll() lifecycle handling without Executor/ML Kit races
 * - Zero-permission Photo Picker image QR scan ("Importer image")
 * - Text-capable manual input fallback (supports both numeric barcodes & konabpos:// QR URIs)
 * - Flashlight/Torch toggle, animated laser reticle, sound & vibration feedback
 */
@Composable
fun RealBarcodeScannerModal(
    title: String = "Scanner de code-barres & QR",
    subtitle: String = "Visez le QR code ou le code-barres pour le scanner",
    continuousScan: Boolean = false,
    lastScannedBarcode: String? = null,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)

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
    var cameraBindError by remember { mutableStateOf<String?>(null) }
    var cameraRetryNonce by remember { mutableIntStateOf(0) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var scanSuccessFeedback by remember { mutableStateOf<String?>(null) }
    var singleScanDelivered by remember { mutableStateOf(false) }

    val imageQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val opts = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = BarcodeScanning.getClient(opts)
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstNotNullOfOrNull {
                            (it.rawValue ?: it.displayValue)?.trim()?.takeIf { s -> s.isNotBlank() }
                        }
                        if (raw != null) {
                            scanSuccessFeedback = raw
                            playScanFeedback(context)
                            currentOnBarcodeScanned(raw)
                        } else {
                            Toast.makeText(
                                context,
                                "Aucun QR code ou code-barres détecté dans cette image.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .addOnFailureListener { err ->
                        Toast.makeText(
                            context,
                            "Erreur d'analyse de l'image : ${err.localizedMessage ?: "inconnue"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnCompleteListener {
                        try {
                            scanner.close()
                        } catch (_: Exception) {}
                    }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Impossible d'ouvrir l'image sélectionnée.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(isTorchEnabled, cameraControl) {
        try {
            cameraControl?.enableTorch(isTorchEnabled)
        } catch (_: Exception) {}
    }

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
            if (hasCameraPermission && cameraBindError == null) {
                // Live CameraX Preview with ML Kit Analyzer
                key(cameraRetryNonce) {
                    CameraScannerPreview(
                        modifier = Modifier.fillMaxSize(),
                        isTorchEnabled = isTorchEnabled,
                        onCameraControlReady = { cameraControl = it },
                        onCameraError = { msg ->
                            cameraBindError = msg
                            showManualInput = true
                        },
                        onBarcodeDetected = { barcode ->
                            if (!continuousScan) {
                                if (singleScanDelivered) return@CameraScannerPreview
                                singleScanDelivered = true
                            }
                            scanSuccessFeedback = barcode
                            playScanFeedback(context)
                            currentOnBarcodeScanned(barcode)
                        }
                    )
                }

                // Dark Scrim Overlay with transparent Viewfinder Reticle
                ViewfinderOverlay(
                    modifier = Modifier.fillMaxSize(),
                    successFeedback = scanSuccessFeedback
                )
            } else {
                // Fallback UI when Camera permission is denied or camera hardware fails
                CameraUnavailableFallback(
                    isPermissionMissing = !hasCameraPermission,
                    errorDetail = cameraBindError,
                    onRequestOrRetry = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            cameraBindError = null
                            cameraRetryNonce++
                        }
                    },
                    onPickImageQr = {
                        imageQrLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onManualSubmit = { code ->
                        if (code.isNotBlank()) {
                            playScanFeedback(context)
                            currentOnBarcodeScanned(code.trim())
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
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("btn_close_scanner")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fermer le scanner",
                        tint = Color.White
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        text = if (continuousScan) "Mode continu actif" else "Scanner QR & Code-barres actif",
                        color = KonabGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (hasCameraPermission && cameraBindError == null) {
                    IconButton(
                        onClick = {
                            val targetState = !isTorchEnabled
                            isTorchEnabled = targetState
                            try {
                                cameraControl?.enableTorch(targetState)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isTorchEnabled) KonabGold else Color.Black.copy(alpha = 0.55f),
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
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Bottom Control and Status Area (shown over live camera preview)
            if (hasCameraPermission && cameraBindError == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Success feedback banner
                    if (scanSuccessFeedback != null || lastScannedBarcode != null) {
                        Surface(
                            color = KonabGreenDark.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
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
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Subtitle help text
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // Action row: Gallery QR Image Picker + Manual Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                imageQrLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Black.copy(alpha = 0.65f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(Color(0xFF38BDF8))
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_pick_qr_image")
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Image QR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { showManualInput = !showManualInput },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Black.copy(alpha = 0.65f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(KonabGold)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_manual_barcode_toggle")
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = KonabGold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (showManualInput) "Masquer" else "Saisie manuelle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
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
                                    placeholder = { Text("Code-barres ou lien QR (konabpos://...)", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (manualBarcode.isNotBlank()) {
                                                playScanFeedback(context)
                                                currentOnBarcodeScanned(manualBarcode.trim())
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
                                            currentOnBarcodeScanned(manualBarcode.trim())
                                            manualBarcode = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_submit_manual_barcode")
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
    onCameraError: (String) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val latestOnCameraControlReady by rememberUpdatedState(onCameraControlReady)
    val latestOnCameraError by rememberUpdatedState(onCameraError)

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val isDisposed = remember { AtomicBoolean(false) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val barcodeScanner = remember { BarcodeScanning.getClient(scannerOptions) }

    var lastDetectedCode by remember { mutableStateOf<String?>(null) }
    var lastDetectionTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            try {
                boundCameraProvider?.unbindAll()
            } catch (_: Exception) {}
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {}
            try {
                barcodeScanner.close()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // COMPATIBLE (TextureView) is essential inside Compose Dialog windows so the camera
                // surface composites properly without turning black behind Compose overlays.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                if (isDisposed.get()) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    boundCameraProvider = cameraProvider

                    val selector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> {
                            latestOnCameraError("Aucune caméra détectée sur cet appareil.")
                            return@addListener
                        }
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isDisposed.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (isDisposed.get()) return@addOnSuccessListener
                                    for (barcode in barcodes) {
                                        val rawValue = (barcode.rawValue ?: barcode.displayValue)?.trim()
                                        if (!rawValue.isNullOrBlank()) {
                                            val now = System.currentTimeMillis()
                                            if (rawValue != lastDetectedCode || now - lastDetectionTime > 1200L) {
                                                lastDetectedCode = rawValue
                                                lastDetectionTime = now
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    if (!isDisposed.get()) {
                                                        latestOnBarcodeDetected(rawValue)
                                                    }
                                                }
                                                break
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    if (!isDisposed.get()) {
                                        Log.e("BarcodeScanner", "Failed to analyze frame", e)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                    latestOnCameraControlReady(camera.cameraControl)
                    camera.cameraControl.enableTorch(isTorchEnabled)
                } catch (e: Exception) {
                    Log.e("BarcodeScanner", "Camera binding failed", e)
                    if (!isDisposed.get()) {
                        latestOnCameraError(
                            e.localizedMessage ?: "Impossible de démarrer la caméra."
                        )
                    }
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

        // Square-ish reticle centered nicely for both 1D barcodes and 2D QR Codes
        val boxWidth = canvasWidth * 0.74f
        val boxHeight = minOf(boxWidth * 0.88f, canvasHeight * 0.42f)
        val boxLeft = (canvasWidth - boxWidth) / 2f
        val boxTop = (canvasHeight - boxHeight) / 2.4f
        val boxRight = boxLeft + boxWidth
        val boxBottom = boxTop + boxHeight
        val cornerRadius = 16.dp.toPx()

        // 1. Darkened scrim with genuine transparent EvenOdd cutout
        val overlayPath = Path().apply {
            fillType = PathFillType.EvenOdd
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
            color = Color.Black.copy(alpha = 0.56f)
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
 * Fallback UI when Camera permission is denied or camera hardware fails
 */
@Composable
private fun CameraUnavailableFallback(
    isPermissionMissing: Boolean,
    errorDetail: String?,
    onRequestOrRetry: () -> Unit,
    onPickImageQr: () -> Unit,
    onManualSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fallbackBarcode by remember { mutableStateOf("") }

    Box(
        modifier = modifier.imePadding(),
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    text = if (isPermissionMissing) "Accès caméra requis" else "Caméra indisponible",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isPermissionMissing) {
                        "Pour scanner les QR codes tables et les codes-barres produits, Konab POS a besoin d'accéder à la caméra."
                    } else {
                        errorDetail ?: "La caméra n'a pas pu être initialisée sur cet appareil."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRequestOrRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_request_camera_permission")
                ) {
                    Icon(
                        if (isPermissionMissing) Icons.Default.CameraAlt else Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPermissionMissing) "Autoriser la caméra" else "Réessayer la caméra")
                }

                OutlinedButton(
                    onClick = onPickImageQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_fallback_pick_qr_image")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanner depuis une image / galerie")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Ou saisissez / collez le code manuellement :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = fallbackBarcode,
                        onValueChange = { fallbackBarcode = it },
                        placeholder = { Text("Code-barres ou konabpos://...", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (fallbackBarcode.isNotBlank()) {
                                    onManualSubmit(fallbackBarcode.trim())
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (fallbackBarcode.isNotBlank()) {
                                onManualSubmit(fallbackBarcode.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreenDark),
                        modifier = Modifier.height(48.dp)
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

        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    } catch (e: Exception) {
        Log.w("BarcodeFeedback", "Feedback error", e)
    }
}
