package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.util.BiometricAvailability
import com.example.util.BiometricSecurityHelper

@Composable
fun PinLockDialog(
    shopName: String = "",
    expectedPinLength: Int = 4,
    onUnlock: (enteredPin: String) -> Boolean,
    onBiometricUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val targetPinLength = expectedPinLength.coerceIn(4, 8)
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val biometricAvailability = remember { BiometricSecurityHelper.checkBiometricAvailability(context) }
    val biometricEnabled = remember { BiometricSecurityHelper.isBiometricEnabled(context) }
    val canUseBiometric = biometricEnabled && biometricAvailability == BiometricAvailability.AVAILABLE

    fun triggerBiometricPrompt() {
        BiometricSecurityHelper.authenticateWithBiometrics(
            context = context,
            title = if (shopName.isNotBlank()) "Déverrouiller $shopName" else "Sécurité KONAB POS",
            subtitle = "Utilisez votre empreinte digitale ou le code Gérant",
            negativeButtonText = "Utiliser le code Gérant",
            onSuccess = {
                errorMessage = ""
                onBiometricUnlock()
            },
            onFallbackPin = {
                // User chose to enter Manager PIN manually
            },
            onError = { msg ->
                errorMessage = msg
            }
        )
    }

    // Automatically prompt biometric authentication at app launch when available and enabled
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) {
            triggerBiometricPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = false) {}
            .safeDrawingPadding()
            .imePadding()
            .testTag("lock_screen_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        border = BorderStroke(2.dp, KonabBlueLight)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_konab_logo),
                            contentDescription = "Logo KONAB POS",
                            modifier = Modifier
                                .padding(3.dp)
                                .fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(KonabBlueDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Application sécurisée",
                            tint = KonabOrangeLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = shopName.ifBlank { "KONAB POS" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = KonabBlue.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = KonabBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (canUseBiometric) {
                                "Code Gérant ou Empreinte Digitale"
                            } else {
                                "Code Gérant requis pour ouvrir l'application"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KonabBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Animated PIN Dots
                val isError = errorMessage.isNotBlank()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(targetPinLength) { index ->
                        val isFilled = index < enteredPin.length
                        val dotSize by animateDpAsState(
                            targetValue = if (isFilled) 20.dp else 16.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "pin_dot_size"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                isError -> KonabRed
                                isFilled -> KonabGreen
                                else -> Color.Transparent
                            },
                            label = "pin_dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    width = 2.dp,
                                    color = if (isError) KonabRed else KonabGreen,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = KonabRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Numeric Keypad for Manager Code
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "C" -> {
                                                enteredPin = ""
                                                errorMessage = ""
                                            }
                                            "DEL" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                    errorMessage = ""
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < targetPinLength) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    errorMessage = ""
                                                    if (newPin.length == targetPinLength) {
                                                        val success = onUnlock(newPin)
                                                        if (!success) {
                                                            errorMessage = "Code Gérant incorrect. Veuillez réessayer."
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .konabPressScale(pressedScale = 0.94f)
                                        .testTag("pin_key_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Effacer",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = if (key == "C") KonabRed else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Fingerprint / Biometric Unlock Button
                if (biometricEnabled && biometricAvailability != BiometricAvailability.NO_HARDWARE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (biometricAvailability == BiometricAvailability.AVAILABLE) {
                        Button(
                            onClick = { triggerBiometricPrompt() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KonabGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .konabPressScale()
                                .testTag("btn_unlock_biometric")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Empreinte digitale",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Déverrouiller par Empreinte Digitale",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else if (biometricAvailability == BiometricAvailability.NONE_ENROLLED) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Aucune empreinte enregistrée dans les paramètres Android — saisissez le Code Gérant.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
