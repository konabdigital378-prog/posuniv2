package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomerEntity
import com.example.data.remote.supabase.CoutureMeasurements
import com.example.data.remote.supabase.CoutureModelRecord
import com.example.data.remote.supabase.CoutureOrderRecord
import com.example.data.remote.supabase.CoutureStage
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.OutfitFontFamily
import com.example.util.CouturePhotoCaptureControl
import com.example.util.CouturePhotoImage
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CoutureViolet = Color(0xFF5B21B6)
private val CoutureVioletLight = Color(0xFFF5F3FF)

private class CoutureOrderFormState(
    initialOrder: CoutureOrderRecord?,
    models: List<CoutureModelRecord>
) {
    var firstName by mutableStateOf(initialOrder?.customerFirstName ?: "")
    var lastName by mutableStateOf(initialOrder?.customerLastName ?: "")
    var phone by mutableStateOf(initialOrder?.customerPhone ?: "")
    var showCustomerPicker by mutableStateOf(false)

    var isCustomClientModel by mutableStateOf(initialOrder?.isCustomClientModel ?: false)
    var selectedModelId by mutableStateOf(initialOrder?.modelId ?: models.firstOrNull()?.id.orEmpty())
    var customModelTitle by mutableStateOf(
        if (initialOrder?.isCustomClientModel == true) initialOrder.modelTitle
        else initialOrder?.modelTitle ?: models.firstOrNull()?.title ?: "Tenue sur-mesure"
    )
    var selectedModelImage by mutableStateOf(
        initialOrder?.modelImageUri ?: models.firstOrNull()?.imageUri ?: "img_prod_mode"
    )

    var fabricPhotoUri by mutableStateOf(initialOrder?.fabricPhotoUri ?: "")
    var fabricNotes by mutableStateOf(initialOrder?.fabricNotes ?: "")

    private val initM = initialOrder?.measurements ?: CoutureMeasurements()
    var bodyProfile by mutableStateOf(initM.bodyProfile)
    var shoulders by mutableStateOf(initM.shoulders)
    var chest by mutableStateOf(initM.chest)
    var waist by mutableStateOf(initM.waist)
    var hips by mutableStateOf(initM.hips)
    var sleeveLength by mutableStateOf(initM.sleeveLength)
    var armCircumference by mutableStateOf(initM.armCircumference)
    var wrist by mutableStateOf(initM.wrist)
    var neck by mutableStateOf(initM.neck)
    var topLength by mutableStateOf(initM.topLength)
    var bottomLength by mutableStateOf(initM.bottomLength)
    var thigh by mutableStateOf(initM.thigh)
    var knee by mutableStateOf(initM.knee)
    var ankle by mutableStateOf(initM.ankle)
    var totalLength by mutableStateOf(initM.totalLength)
    var measurementNotes by mutableStateOf(initM.extraNotes)

    var appointmentAt by mutableLongStateOf(
        initialOrder?.appointmentAt?.takeIf { it > 0L } ?: (System.currentTimeMillis() + 5L * 86_400_000L)
    )
    var appointmentNote by mutableStateOf(
        initialOrder?.appointmentNote ?: "Essayage & récupération à l'atelier"
    )

    var priceText by mutableStateOf(
        (initialOrder?.totalPrice?.takeIf { it > 0L }
            ?: models.firstOrNull { it.id == selectedModelId }?.price
            ?: 15000L).toString()
    )
    var advanceText by mutableStateOf(
        (initialOrder?.advancePaid ?: 0L).takeIf { it > 0L }?.toString() ?: ""
    )
    var selectedStage by mutableStateOf(
        if (initialOrder == null || initialOrder.status == CoutureStage.DEMANDE_CLIENT) {
            CoutureStage.MESURES_PRISES
        } else {
            initialOrder.status
        }
    )
    var tailorNotes by mutableStateOf(initialOrder?.tailorNotes ?: "")
    var validationError by mutableStateOf<String?>(null)
}

@Composable
fun CoutureMeasurementsModal(
    initialOrder: CoutureOrderRecord?,
    models: List<CoutureModelRecord>,
    customers: List<CustomerEntity>,
    currency: String = "FCFA",
    shopId: Long = 0L,
    shopName: String = "",
    onSave: (CoutureOrderRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val orderKey = initialOrder?.id
    val form = remember(orderKey) { CoutureOrderFormState(initialOrder, models) }
    val apptFormatter = remember { SimpleDateFormat("EEE dd MMM yyyy 'à' HH:mm", Locale.FRENCH) }

    fun openDateAndTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = form.appointmentAt }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        form.appointmentAt = cal.timeInMillis
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("modal_couture_measurements"),
            topBar = {
                CoutureMeasurementsTopBar(
                    initialOrder = initialOrder,
                    onDismiss = onDismiss
                )
            },
            bottomBar = {
                CoutureMeasurementsBottomBar(
                    form = form,
                    initialOrder = initialOrder,
                    models = models,
                    shopId = shopId,
                    shopName = shopName,
                    onSave = onSave,
                    onDismiss = onDismiss
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoutureClientInfoSection(form = form, customers = customers)
                CoutureModelSection(form = form, initialOrder = initialOrder, models = models, currency = currency)
                CoutureFabricPhotoSection(form = form)
                CoutureMeasurementsGridSection(form = form)
                CoutureAppointmentAndStageSection(
                    form = form,
                    currency = currency,
                    apptFormatter = apptFormatter,
                    onPickDateTime = { openDateAndTimePicker() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CoutureMeasurementsTopBar(
    initialOrder: CoutureOrderRecord?,
    onDismiss: () -> Unit
) {
    Surface(color = CoutureViolet, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                }
                Column {
                    Text(
                        text = if (initialOrder == null) "Prise de Mesures & Fiche Client" else "Fiche Couture ${initialOrder.orderNumber}",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Mensurations, photo du tissu et date de rendez-vous",
                        fontSize = 11.sp,
                        color = Color(0xFFDDD6FE)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoutureMeasurementsBottomBar(
    form: CoutureOrderFormState,
    initialOrder: CoutureOrderRecord?,
    models: List<CoutureModelRecord>,
    shopId: Long,
    shopName: String,
    onSave: (CoutureOrderRecord) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            form.validationError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(50.dp)
                ) {
                    Text("Annuler", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (form.firstName.isBlank() && form.lastName.isBlank()) {
                            form.validationError = "Renseignez au moins le prénom ou nom du client."
                            return@Button
                        }
                        if (form.phone.isBlank()) {
                            form.validationError = "Le numéro de téléphone du client est requis pour le suivi."
                            return@Button
                        }
                        val totalPrice = form.priceText.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val advance = form.advanceText.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val chosenTitle = if (form.isCustomClientModel) {
                            form.customModelTitle.trim().ifBlank { "Modèle apporté par le client" }
                        } else {
                            models.firstOrNull { it.id == form.selectedModelId }?.title
                                ?: form.customModelTitle.ifBlank { "Tenue sur-mesure" }
                        }
                        val meas = CoutureMeasurements(
                            bodyProfile = form.bodyProfile,
                            shoulders = form.shoulders.trim(),
                            chest = form.chest.trim(),
                            waist = form.waist.trim(),
                            hips = form.hips.trim(),
                            sleeveLength = form.sleeveLength.trim(),
                            armCircumference = form.armCircumference.trim(),
                            wrist = form.wrist.trim(),
                            neck = form.neck.trim(),
                            topLength = form.topLength.trim(),
                            bottomLength = form.bottomLength.trim(),
                            thigh = form.thigh.trim(),
                            knee = form.knee.trim(),
                            ankle = form.ankle.trim(),
                            totalLength = form.totalLength.trim(),
                            extraNotes = form.measurementNotes.trim()
                        )
                        val built = CoutureOrderRecord(
                            id = initialOrder?.id.orEmpty(),
                            shopId = shopId,
                            shopName = initialOrder?.shopName?.ifBlank { shopName } ?: shopName,
                            orderNumber = initialOrder?.orderNumber.orEmpty(),
                            customerFirstName = form.firstName.trim(),
                            customerLastName = form.lastName.trim(),
                            customerPhone = form.phone.trim(),
                            modelId = if (form.isCustomClientModel) "" else form.selectedModelId,
                            modelTitle = chosenTitle,
                            modelImageUri = if (form.isCustomClientModel) {
                                form.fabricPhotoUri.ifBlank { "img_prod_mode" }
                            } else {
                                form.selectedModelImage
                            },
                            isCustomClientModel = form.isCustomClientModel,
                            fabricPhotoUri = form.fabricPhotoUri.trim(),
                            fabricNotes = form.fabricNotes.trim(),
                            measurements = meas,
                            status = form.selectedStage,
                            appointmentAt = form.appointmentAt,
                            appointmentNote = form.appointmentNote.trim(),
                            totalPrice = totalPrice,
                            advancePaid = advance,
                            saleId = initialOrder?.saleId,
                            tailorNotes = form.tailorNotes.trim(),
                            createdAt = initialOrder?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(built)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoutureViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(50.dp)
                        .testTag("btn_save_couture_order")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enregistrer la fiche", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun CoutureClientInfoSection(
    form: CoutureOrderFormState,
    customers: List<CustomerEntity>
) {
    SectionCard(
        title = "1. Coordonnées du Client",
        icon = Icons.Default.Person
    ) {
        if (customers.isNotEmpty()) {
            OutlinedButton(
                onClick = { form.showCustomerPicker = !form.showCustomerPicker },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (form.showCustomerPicker) "Masquer mes clients" else "Choisir un client enregistré (${customers.size})")
            }
            if (form.showCustomerPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customers.take(15).forEach { cust ->
                        AssistChip(
                            onClick = {
                                val parts = cust.name.trim().split(" ", limit = 2)
                                form.firstName = parts.getOrNull(0).orEmpty()
                                form.lastName = parts.getOrNull(1).orEmpty()
                                form.phone = cust.phone
                                form.showCustomerPicker = false
                            },
                            label = { Text("${cust.name} • ${cust.phone}") }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = form.firstName,
                onValueChange = { form.firstName = it; form.validationError = null },
                label = { Text("Prénom *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_couture_firstname")
            )
            OutlinedTextField(
                value = form.lastName,
                onValueChange = { form.lastName = it; form.validationError = null },
                label = { Text("Nom *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_couture_lastname")
            )
        }

        OutlinedTextField(
            value = form.phone,
            onValueChange = { form.phone = it; form.validationError = null },
            label = { Text("Numéro de téléphone (utilisé pour le suivi) *") },
            placeholder = { Text("Ex: 70 12 34 56") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_couture_phone")
        )
    }
}

@Composable
private fun CoutureModelSection(
    form: CoutureOrderFormState,
    initialOrder: CoutureOrderRecord?,
    models: List<CoutureModelRecord>,
    currency: String
) {
    SectionCard(
        title = "2. Modèle de Tenue",
        icon = Icons.Default.Checkroom
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !form.isCustomClientModel,
                onClick = { form.isCustomClientModel = false },
                label = { Text("Catalogue Atelier", fontWeight = FontWeight.Bold) },
                leadingIcon = if (!form.isCustomClientModel) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = form.isCustomClientModel,
                onClick = { form.isCustomClientModel = true },
                label = { Text("Modèle apporté par client", fontWeight = FontWeight.Bold) },
                leadingIcon = if (form.isCustomClientModel) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }

        if (!form.isCustomClientModel && models.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                models.forEach { mod ->
                    val isSel = mod.id == form.selectedModelId
                    Surface(
                        onClick = {
                            form.selectedModelId = mod.id
                            form.customModelTitle = mod.title
                            form.selectedModelImage = mod.imageUri
                            if (form.priceText.isBlank() || form.priceText == "0" || initialOrder == null) {
                                form.priceText = mod.price.toString()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSel) CoutureVioletLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            if (isSel) 2.dp else 1.dp,
                            if (isSel) CoutureViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            CouturePhotoImage(
                                uriOrKey = mod.imageUri,
                                contentDescription = mod.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = mod.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = CurrencyFormatter.formatAmount(mod.price, currency),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CoutureViolet
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = form.customModelTitle,
                onValueChange = { form.customModelTitle = it },
                label = { Text("Nom / description du modèle souhaité") },
                placeholder = { Text("Ex: Robe soirée fendue + veste cintrée") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CoutureFabricPhotoSection(form: CoutureOrderFormState) {
    SectionCard(
        title = "3. Photo du Tissu Récupéré",
        icon = Icons.Default.CameraAlt
    ) {
        CouturePhotoCaptureControl(
            title = "Tissu remis par le client",
            subtitle = "Prenez une photo directe du pagne / bazin / tissu pour le retrouver et le montrer au client dans son espace",
            currentPhotoUri = form.fabricPhotoUri,
            onPhotoSelected = { uri -> form.fabricPhotoUri = uri },
            filePrefix = "tissu",
            cameraButtonTestTag = "btn_couture_take_fabric_photo",
            galleryButtonTestTag = "btn_couture_pick_fabric_gallery"
        )
        OutlinedTextField(
            value = form.fabricNotes,
            onValueChange = { form.fabricNotes = it },
            label = { Text("Notes sur le tissu (métrage, couleur, doublure...)") },
            placeholder = { Text("Ex: 3 pagnes Bazin blanc + fil or, doublure coton") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoutureMeasurementsGridSection(form: CoutureOrderFormState) {
    SectionCard(
        title = "4. Outils de Prise de Mesures (cm)",
        icon = Icons.Default.Straighten
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Femme", "Homme", "Enfant").forEach { prof ->
                FilterChip(
                    selected = form.bodyProfile == prof,
                    onClick = { form.bodyProfile = prof },
                    label = { Text(prof, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        MeasurementRow(
            labelA = "Épaules (cm)", valueA = form.shoulders, onChangeA = { form.shoulders = it },
            labelB = "Poitrine / Buste", valueB = form.chest, onChangeB = { form.chest = it }
        )
        MeasurementRow(
            labelA = "Tour de taille", valueA = form.waist, onChangeA = { form.waist = it },
            labelB = "Tour de hanches", valueB = form.hips, onChangeB = { form.hips = it }
        )
        MeasurementRow(
            labelA = "Long. manches", valueA = form.sleeveLength, onChangeA = { form.sleeveLength = it },
            labelB = "Tour de bras", valueB = form.armCircumference, onChangeB = { form.armCircumference = it }
        )
        MeasurementRow(
            labelA = "Poignet", valueA = form.wrist, onChangeA = { form.wrist = it },
            labelB = "Encolure / Col", valueB = form.neck, onChangeB = { form.neck = it }
        )
        MeasurementRow(
            labelA = "Long. haut / boubou", valueA = form.topLength, onChangeA = { form.topLength = it },
            labelB = "Long. pantalon/jupe", valueB = form.bottomLength, onChangeB = { form.bottomLength = it }
        )
        MeasurementRow(
            labelA = "Tour de cuisse", valueA = form.thigh, onChangeA = { form.thigh = it },
            labelB = "Tour de genou", valueB = form.knee, onChangeB = { form.knee = it }
        )
        MeasurementRow(
            labelA = "Bas / Cheville", valueA = form.ankle, onChangeA = { form.ankle = it },
            labelB = "Long. totale robe", valueB = form.totalLength, onChangeB = { form.totalLength = it }
        )

        OutlinedTextField(
            value = form.measurementNotes,
            onValueChange = { form.measurementNotes = it },
            label = { Text("Précisions de coupe & morphologie") },
            placeholder = { Text("Ex: Pince poitrine prononcée, col mao 4cm, taille ajustée") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoutureAppointmentAndStageSection(
    form: CoutureOrderFormState,
    currency: String,
    apptFormatter: SimpleDateFormat,
    onPickDateTime: () -> Unit
) {
    SectionCard(
        title = "5. Rendez-vous, Tarif & Étape",
        icon = Icons.Default.EventAvailable
    ) {
        Surface(
            onClick = onPickDateTime,
            shape = RoundedCornerShape(12.dp),
            color = CoutureVioletLight,
            border = BorderStroke(1.dp, CoutureViolet.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("btn_pick_couture_appointment")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = CoutureViolet)
                    Column {
                        Text("Date & heure du rendez-vous", fontSize = 11.sp, color = CoutureViolet)
                        Text(
                            text = apptFormatter.format(Date(form.appointmentAt)),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF3B0764)
                        )
                    }
                }
                Text("Modifier", fontWeight = FontWeight.Bold, color = CoutureViolet, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(3 to "+3 jours", 5 to "+5 jours", 7 to "+7 jours", 14 to "+14 jours").forEach { (days, label) ->
                AssistChip(
                    onClick = {
                        form.appointmentAt = System.currentTimeMillis() + days.toLong() * 86_400_000L
                    },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        OutlinedTextField(
            value = form.appointmentNote,
            onValueChange = { form.appointmentNote = it },
            label = { Text("Consigne rendez-vous") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = form.priceText,
                onValueChange = { form.priceText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Prix confection ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = form.advanceText,
                onValueChange = { form.advanceText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Avance reçue") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        val totalP = form.priceText.toLongOrNull() ?: 0L
        val advP = form.advanceText.toLongOrNull() ?: 0L
        val remain = (totalP - advP).coerceAtLeast(0L)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = KonabGreen.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Reste à payer à la livraison : ${CurrencyFormatter.formatAmount(remain, currency)}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = KonabGreen,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Text("Étape actuelle de la couture", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoutureStage.entries.forEach { st ->
                FilterChip(
                    selected = form.selectedStage == st,
                    onClick = { form.selectedStage = st },
                    label = { Text("${st.progressPercent}% ${st.shortLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        OutlinedTextField(
            value = form.tailorNotes,
            onValueChange = { form.tailorNotes = it },
            label = { Text("Message atelier visible dans le suivi client") },
            placeholder = { Text("Ex: Broderie dorée validée avec le client") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CoutureVioletLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = CoutureViolet, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun MeasurementRow(
    labelA: String,
    valueA: String,
    onChangeA: (String) -> Unit,
    labelB: String,
    valueB: String,
    onChangeB: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = valueA,
            onValueChange = onChangeA,
            label = { Text(labelA, fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = valueB,
            onValueChange = onChangeB,
            label = { Text(labelB, fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
    }
}
