package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.ui.screens.AgentUser
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

data class AgentTariffEstimate(
    val priceFcfa: Long,
    val commissionFcfa: Long,
    val formattedPrice: String,
    val formattedCommission: String
)

object AgentFieldHelper {
    val QUICK_CITIES = listOf(
        "Ouagadougou",
        "Bobo-Dioulasso",
        "Koudougou",
        "Ouahigouya",
        "Banfora",
        "Kaya",
        "Tenkodogo",
        "Dédougou"
    )

    val QUICK_PAYMENT_METHODS = listOf(
        "Orange Money",
        "Moov Money",
        "Wave",
        "Coris Money",
        "Espèces"
    )

    val QUICK_CURRENCIES = listOf("FCFA", "GNF", "EUR", "USD")

    fun estimateTariff(tier: String, durationDays: Int): AgentTariffEstimate {
        val monthlyBase = when (tier.lowercase(Locale.ROOT)) {
            "standard", "std" -> 5000L
            "professionnel", "pro" -> 10000L
            "entreprise", "ent" -> 25000L
            "premium", "vip" -> 50000L
            else -> 10000L
        }
        val multiplier = when (durationDays) {
            30 -> 1L
            90 -> 3L
            180 -> 5L   // 1 mois offert sur 6 mois
            365 -> 10L  // 2 mois offerts sur l'an
            3650 -> 24L // Forfait licence permanente
            else -> (durationDays / 30L).coerceAtLeast(1L)
        }
        val price = monthlyBase * multiplier
        val commission = (price * 20L) / 100L
        val fmt = NumberFormat.getNumberInstance(Locale.FRANCE)
        return AgentTariffEstimate(
            priceFcfa = price,
            commissionFcfa = commission,
            formattedPrice = "${fmt.format(price)} FCFA",
            formattedCommission = "${fmt.format(commission)} FCFA"
        )
    }

    fun calculatePortfolioStats(deployments: List<AgentDeploymentRecord>): Triple<Int, Int, String> {
        val synced = deployments.count { it.isSynced }
        val pending = deployments.size - synced
        val totalCommission = deployments.sumOf { estimateTariff(it.tier, it.durationDays).commissionFcfa }
        val fmt = NumberFormat.getNumberInstance(Locale.FRANCE)
        return Triple(synced, pending, "${fmt.format(totalCommission)} F")
    }

    fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String? = null) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toastMessage ?: "$label copié dans le presse-papier", Toast.LENGTH_SHORT).show()
    }

    fun shareAttestation(
        context: Context,
        shopName: String,
        shopCity: String,
        tier: String,
        durationDays: Int,
        licenseKey: String,
        agentName: String,
        agentPhone: String,
        dateLabel: String? = null
    ) {
        val shareText = buildString {
            appendLine("🧾 *ATTESTATION DE DÉPLOIEMENT KONAB POS*")
            appendLine()
            appendLine("Boutique : ${shopName.ifBlank { "Client Konab POS" }}")
            if (shopCity.isNotBlank()) appendLine("Ville : $shopCity")
            appendLine("Formule : $tier ($durationDays jours)")
            if (licenseKey.isNotBlank()) appendLine("Clé de licence : $licenseKey")
            appendLine("Agent Déployeur : $agentName${if (agentPhone.isNotBlank()) " ($agentPhone)" else ""}")
            if (!dateLabel.isNullOrBlank()) appendLine("Date : $dateLabel")
            appendLine()
            append("Votre caisse enregistreuse Konab POS est officiellement activée et prête pour vos ventes quotidiennes !")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Attestation de déploiement Konab POS")
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, "Transmettre l'attestation"))
    }

    fun dialPhone(context: Context, phone: String) {
        val clean = phone.replace(" ", "").trim()
        if (clean.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir le composeur téléphonique", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AgentKpiSummaryStrip(
    totalDeployments: Int,
    syncedCount: Int,
    pendingCount: Int,
    estimatedCommissionLabel: String,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgentKpiTile(
                    icon = Icons.Default.Storefront,
                    label = "Boutiques",
                    value = "$totalDeployments",
                    accentColor = KonabBlue,
                    modifier = Modifier.weight(1f)
                )
                AgentKpiTile(
                    icon = if (pendingCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                    label = if (pendingCount > 0) "En attente" else "Cloud OK",
                    value = if (pendingCount > 0) "$pendingCount à sync" else "$syncedCount/$totalDeployments",
                    accentColor = if (pendingCount > 0) KonabOrange else KonabGreen,
                    modifier = Modifier.weight(1f)
                )
                AgentKpiTile(
                    icon = Icons.Default.Payments,
                    label = "Primes est.",
                    value = estimatedCommissionLabel,
                    accentColor = KonabGoldDark,
                    modifier = Modifier.weight(1f)
                )
            }

            if (pendingCount > 0 || isSyncing) {
                Surface(
                    onClick = { if (!isSyncing) onSyncClick() },
                    shape = RoundedCornerShape(10.dp),
                    color = KonabOrange.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = KonabOrange
                                )
                            } else {
                                KonabLivePulseDot(color = KonabOrange, size = 7.dp)
                            }
                            Text(
                                text = if (isSyncing) "Synchronisation Cloud en cours..." else "$pendingCount déploiement(s) hors-ligne à envoyer",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!isSyncing) {
                            Text(
                                text = "Synchroniser",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KonabOrange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentKpiTile(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EditAgentProfileDialog(
    agent: AgentUser,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AgentUser) -> Unit
) {
    var name by remember(agent) { mutableStateOf(agent.name) }
    var phone by remember(agent) { mutableStateOf(agent.phone) }
    var zone by remember(agent) { mutableStateOf(agent.zone) }
    var photoUri by remember(agent) { mutableStateOf(agent.photoUri) }
    var newPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = KonabOrange)
                Column {
                    Text("Modifier mon Profil Agent", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Matricule : ${agent.id}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AgentPhotoUploadRow(
                    agentName = name.ifBlank { agent.name },
                    photoUri = photoUri,
                    onPhotoSelected = { photoUri = it },
                    agentKeyHint = agent.id
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = "" },
                    label = { Text("Nom complet *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = { Text("Téléphone / WhatsApp *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it; errorMsg = "" },
                    label = { Text("Ville / Zone d'intervention *") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AgentFieldHelper.QUICK_CITIES) { city ->
                        FilterChip(
                            selected = zone.equals(city, ignoreCase = true),
                            onClick = { zone = city },
                            label = { Text(city, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it; errorMsg = "" },
                    label = { Text("Nouveau code PIN (optionnel)") },
                    placeholder = { Text("Laisser vide pour conserver") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPin) "Masquer" else "Afficher"
                            )
                        }
                    },
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotBlank()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        errorMsg = "Nom et téléphone obligatoires."
                        return@Button
                    }
                    if (newPin.isNotBlank() && newPin.trim().length < 4) {
                        errorMsg = "Le code PIN doit comporter au moins 4 chiffres."
                        return@Button
                    }
                    onSave(
                        agent.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            zone = zone.trim().ifBlank { "National" },
                            pin = if (newPin.isNotBlank()) newPin.trim() else agent.pin,
                            photoUri = photoUri
                        )
                    )
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = KonabOrange)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Annuler")
            }
        }
    )
}
