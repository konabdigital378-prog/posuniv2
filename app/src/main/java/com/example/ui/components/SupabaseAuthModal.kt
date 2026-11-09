package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.SupabaseAuthManager
import com.example.data.remote.supabase.SupabaseConfig
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseAuthModal(
    authState: SupabaseAuthManager.AuthState,
    onSignIn: (email: String, pass: String) -> Unit,
    onSignUp: (email: String, pass: String, name: String, phone: String, role: String, shopName: String) -> Unit,
    onSignOut: () -> Unit,
    onRecoverPassword: (email: String) -> Unit,
    onRefreshSession: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Connexion, 1: Inscription, 2: Mot de passe oublié

    // Diagnostic State for testing directly inside the application
    var diagnosticResult by remember { mutableStateOf<SupabaseAuthManager.DiagnosticResult?>(null) }
    var isRunningDiagnostic by remember { mutableStateOf(false) }

    // Sign In Fields
    var signInEmail by remember { mutableStateOf(SupabaseAuthManager.currentUser?.email ?: "") }
    var signInPassword by remember { mutableStateOf("") }
    var showSignInPassword by remember { mutableStateOf(false) }

    // Sign Up Fields
    var signUpName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpShopName by remember { mutableStateOf("") }
    var signUpRole by remember { mutableStateOf("ADMIN") } // ADMIN or CASHIER
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpPasswordConfirm by remember { mutableStateOf("") }
    var showSignUpPassword by remember { mutableStateOf(false) }

    // Password Recovery Fields
    var recoveryEmail by remember { mutableStateOf("") }

    val isAuthenticated = authState is SupabaseAuthManager.AuthState.Authenticated
    val isLoading = authState is SupabaseAuthManager.AuthState.Loading

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("dialog_supabase_auth"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Surface(
                    color = Color(0xFF1E293B), // Dark slate / Supabase style
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3ECF8E)), // Supabase green
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Compte Cloud Konab",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isAuthenticated) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAuthenticated) "Session Cloud Active" else "Déconnecté",
                                        fontSize = 11.sp,
                                        color = if (isAuthenticated) Color(0xFF3ECF8E) else Color(0xFFFBBF24),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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

                // If loading bar
                if (isLoading || isRunningDiagnostic) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF3ECF8E)
                    )
                }

                // Error message banner if any
                if (authState is SupabaseAuthManager.AuthState.Error) {
                    Surface(
                        color = KonabRedContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = KonabRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = authState.message,
                                fontSize = 12.sp,
                                color = KonabRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Content Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Diagnostic Report Card (if test was triggered)
                    diagnosticResult?.let { diag ->
                        SupabaseDiagnosticCard(
                            diag = diag,
                            onClose = { diagnosticResult = null }
                        )
                    }

                    if (isAuthenticated) {
                        // ==========================================
                        // VIEW: AUTHENTICATED USER PROFILE
                        // ==========================================
                        val session = (authState as SupabaseAuthManager.AuthState.Authenticated).session
                        val user = session.user

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // User Avatar
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3ECF8E).copy(alpha = 0.2f))
                                        .border(2.dp, Color(0xFF3ECF8E), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.take(2).uppercase(),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp,
                                        color = Color(0xFF065F46)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = user.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = user.email,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    color = if (user.role == "ADMIN") KonabBlueContainer else KonabGreenLight,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (user.role == "ADMIN") "GÉRANT / ADMINISTRATEUR" else "CAISSIER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (user.role == "ADMIN") KonabBlue else KonabGreenDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = KonabBorder)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Session details
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ID Utilisateur Supabase :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = user.id.take(12) + "...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copier",
                                                tint = KonabBlue,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("ID Supabase", user.id))
                                                        Toast.makeText(context, "ID copié", Toast.LENGTH_SHORT).show()
                                                    }
                                            )
                                        }
                                    }

                                    if (user.phone.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Téléphone lié :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(user.phone, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Expiration du jeton :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            CurrencyFormatter.formatDateTime(session.expiresAt),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // In-App Test Button (Live diagnostic)
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isRunningDiagnostic = true
                                            diagnosticResult = SupabaseAuthManager.runSelfTest()
                                            isRunningDiagnostic = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tester la connexion depuis l'application", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onRefreshSession,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Actualiser", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = onSignOut,
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabRed),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Déconnexion", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // ==========================================
                        // VIEW: UNAUTHENTICATED TABS
                        // ==========================================
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = Color(0xFF10B981)
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Se connecter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("S'inscrire", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Aide / MDP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        when (selectedTab) {
                            0 -> {
                                // SIGN IN FORM
                                Text("Connexion à votre espace Cloud :", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                OutlinedTextField(
                                    value = signInEmail,
                                    onValueChange = { signInEmail = it },
                                    label = { Text("Adresse Email") },
                                    placeholder = { Text("ex: gerant@konabpos.com") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = signInPassword,
                                    onValueChange = { signInPassword = it },
                                    label = { Text("Mot de passe") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showSignInPassword = !showSignInPassword }) {
                                            Icon(
                                                imageVector = if (showSignInPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    visualTransformation = if (showSignInPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quick demo fill for easy mobile test
                                    TextButton(onClick = {
                                        signInEmail = "demo.gerant@konabpos.com"
                                        signInPassword = "KonabPos2026!"
                                        Toast.makeText(context, "Identifiants de test renseignés !", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = KonabGoldDark, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Compte test démo", fontSize = 11.5.sp, color = KonabGoldDark, fontWeight = FontWeight.Bold)
                                    }

                                    TextButton(onClick = { selectedTab = 2 }) {
                                        Text("Mot de passe oublié ?", fontSize = 11.5.sp, color = KonabBlue)
                                    }
                                }

                                Button(
                                    onClick = {
                                        onSignIn(signInEmail.trim(), signInPassword.trim())
                                    },
                                    enabled = !isLoading && signInEmail.isNotBlank() && signInPassword.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_supabase_submit_login")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Se connecter à Supabase", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // In-App Direct Diagnostic Button
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isRunningDiagnostic = true
                                            diagnosticResult = SupabaseAuthManager.runSelfTest()
                                            isRunningDiagnostic = false
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5)),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF818CF8)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tester la connexion depuis l'application", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            1 -> {
                                // SIGN UP FORM
                                Text("Créer un compte Supabase :", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                OutlinedTextField(
                                    value = signUpName,
                                    onValueChange = { signUpName = it },
                                    label = { Text("Nom et Prénom du gestionnaire") },
                                    placeholder = { Text("Ex: Moussa Ouédraogo") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = signUpShopName,
                                    onValueChange = { signUpShopName = it },
                                    label = { Text("Nom de la Boutique") },
                                    placeholder = { Text("Ex: Boutique Kaba") },
                                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = signUpPhone,
                                    onValueChange = { signUpPhone = it },
                                    label = { Text("Téléphone (avec indicatif)") },
                                    placeholder = { Text("Ex: 65413799") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Text("Rôle dans le point de vente :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = signUpRole == "ADMIN",
                                        onClick = { signUpRole = "ADMIN" },
                                        label = { Text("Gérant / Super Admin") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = signUpRole == "CASHIER",
                                        onClick = { signUpRole = "CASHIER" },
                                        label = { Text("Caissier") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedTextField(
                                    value = signUpEmail,
                                    onValueChange = { signUpEmail = it },
                                    label = { Text("Adresse Email") },
                                    placeholder = { Text("Ex: gerant@gmail.com") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = signUpPassword,
                                    onValueChange = { signUpPassword = it },
                                    label = { Text("Mot de passe (6 car. minimum)") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (showSignUpPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = signUpPasswordConfirm,
                                    onValueChange = { signUpPasswordConfirm = it },
                                    label = { Text("Confirmer le mot de passe") },
                                    leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null) },
                                    visualTransformation = if (showSignUpPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = {
                                        if (signUpPassword != signUpPasswordConfirm) {
                                            Toast.makeText(context, "Les deux mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        onSignUp(
                                            signUpEmail.trim(),
                                            signUpPassword.trim(),
                                            signUpName.trim(),
                                            signUpPhone.trim(),
                                            signUpRole,
                                            signUpShopName.trim()
                                        )
                                    },
                                    enabled = !isLoading && signUpEmail.isNotBlank() && signUpPassword.length >= 6,
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_supabase_submit_signup")
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Créer mon compte Cloud", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            2 -> {
                                // RECOVERY
                                Text("Réinitialisation de mot de passe :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Un lien sécurisé de réinitialisation vous sera envoyé par email via Supabase.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = recoveryEmail,
                                    onValueChange = { recoveryEmail = it },
                                    label = { Text("Votre adresse email") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = {
                                        onRecoverPassword(recoveryEmail.trim())
                                    },
                                    enabled = !isLoading && recoveryEmail.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGoldDark),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Envoyer l'email de réinitialisation", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Cloud security and protection assurance card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Protection & Sauvegarde Sécurisée",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Vos données et transactions sont chiffrées de bout en bout et protégées sur le Cloud.",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF64748B)
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

@Composable
private fun SupabaseDiagnosticCard(
    diag: SupabaseAuthManager.DiagnosticResult,
    onClose: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (diag.isSuccessful) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (diag.isSuccessful) Color(0xFF22C55E) else Color(0xFFEF4444)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (diag.isSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (diag.isSuccessful) Color(0xFF16A34A) else Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (diag.isSuccessful) "Diagnostic Réussi (${diag.latencyMs}ms)" else "Échec du Test",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (diag.isSuccessful) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = if (diag.isSuccessful) Color(0xFFBBF7D0) else Color(0xFFFECACA))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Service de Sécurité :", fontSize = 11.5.sp, color = Color.DarkGray)
                    Text(diag.authStatus, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF15803D))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Synchronisation des Données :", fontSize = 11.5.sp, color = Color.DarkGray)
                    Text(diag.databaseStatus, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF15803D))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Statut Session :", fontSize = 11.5.sp, color = Color.DarkGray)
                    Text(diag.sessionStatus, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                diag.details.forEach { detail ->
                    Text(
                        text = detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
