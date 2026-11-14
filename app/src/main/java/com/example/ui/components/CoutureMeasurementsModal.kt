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

    var firstName by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.customerFirstName ?: "")
    }
    var lastName by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.customerLastName ?: "")
    }
    var phone by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.customerPhone ?: "")
    }
    var showCustomerPicker by remember { mutableStateOf(false) }

    var isCustomClientModel by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.isCustomClientModel ?: false)
    }
    var selectedModelId by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.modelId ?: models.firstOrNull()?.id.orEmpty())
    }
    var customModelTitle by remember(initialOrder?.id) {
        mutableStateOf(
            if (initialOrder?.isCustomClientModel == true) initialOrder.modelTitle
            else initialOrder?.modelTitle ?: models.firstOrNull()?.title ?: "Tenue sur-mesure"
        )
    }
    var selectedModelImage by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.modelImageUri ?: models.firstOrNull()?.imageUri ?: "img_prod_mode")
    }

    var fabricPhotoUri by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.fabricPhotoUri ?: "")
    }
    var fabricNotes by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.fabricNotes ?: "")
    }

    val initM = initialOrder?.measurements ?: CoutureMeasurements()
    var bodyProfile by remember(initialOrder?.id) { mutableStateOf(initM.bodyProfile) }
    var shoulders by remember(initialOrder?.id) { mutableStateOf(initM.shoulders) }
    var chest by remember(initialOrder?.id) { mutableStateOf(initM.chest) }
    var waist by remember(initialOrder?.id) { mutableStateOf(initM.waist) }
    var hips by remember(initialOrder?.id) { mutableStateOf(initM.hips) }
    var sleeveLength by remember(initialOrder?.id) { mutableStateOf(initM.sleeveLength) }
    var armCircumference by remember(initialOrder?.id) { mutableStateOf(initM.armCircumference) }
    var wrist by remember(initialOrder?.id) { mutableStateOf(initM.wrist) }
    var neck by remember(initialOrder?.id) { mutableStateOf(initM.neck) }
    var topLength by remember(initialOrder?.id) { mutableStateOf(initM.topLength) }
    var bottomLength by remember(initialOrder?.id) { mutableStateOf(initM.bottomLength) }
    var thigh by remember(initialOrder?.id) { mutableStateOf(initM.thigh) }
    var knee by remember(initialOrder?.id) { mutableStateOf(initM.knee) }
    var ankle by remember(initialOrder?.id) { mutableStateOf(initM.ankle) }
    var totalLength by remember(initialOrder?.id) { mutableStateOf(initM.totalLength) }
    var measurementNotes by remember(initialOrder?.id) { mutableStateOf(initM.extraNotes) }

    var appointmentAt by remember(initialOrder?.id) {
        val defaultAppt = System.currentTimeMillis() + 5L * 86_400_000L
        mutableLongStateOf(initialOrder?.appointmentAt?.takeIf { it > 0L } ?: defaultAppt)
    }
    var appointmentNote by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.appointmentNote ?: "Essayage & récupération à l'atelier")
    }

    var priceText by remember(initialOrder?.id) {
        val defaultPrice = initialOrder?.totalPrice?.takeIf { it > 0L }
            ?: models.firstOrNull { it.id == selectedModelId }?.price
            ?: 15000L
        mutableStateOf(defaultPrice.toString())
    }
    var advanceText by remember(initialOrder?.id) {
        mutableStateOf((initialOrder?.advancePaid ?: 0L).takeIf { it > 0L }?.toString() ?: "")
    }
    var selectedStage by remember(initialOrder?.id) {
        mutableStateOf(
            if (initialOrder == null || initialOrder.status == CoutureStage.DEMANDE_CLIENT) {
                CoutureStage.MESURES_PRISES
            } else {
                initialOrder.status
            }
        )
    }
    var tailorNotes by remember(initialOrder?.id) {
        mutableStateOf(initialOrder?.tailorNotes ?: "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val apptFormatter = remember { SimpleDateFormat("EEE dd MMM yyyy 'à' HH:mm", Locale.FRENCH) }

    fun openDateAndTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = appointmentAt }
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
                        appointmentAt = cal.timeInMillis
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
            },
            bottomBar = {
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
                        validationError?.let { err ->
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
                                    if (firstName.isBlank() && lastName.isBlank()) {
                                        validationError = "Renseignez au moins le prénom ou nom du client."
                                        return@Button
                                    }
                                    if (phone.isBlank()) {
                                        validationError = "Le numéro de téléphone du client est requis pour le suivi."
                                        return@Button
                                    }
                                    val totalPrice = priceText.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                    val advance = advanceText.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                    val chosenTitle = if (isCustomClientModel) {
                                        customModelTitle.trim().ifBlank { "Modèle apporté par le client" }
                                    } else {
                                        models.firstOrNull { it.id == selectedModelId }?.title ?: customModelTitle.ifBlank { "Tenue sur-mesure" }
                                    }
                                    val meas = CoutureMeasurements(
                                        bodyProfile = bodyProfile,
                                        shoulders = shoulders.trim(),
                                        chest = chest.trim(),
                                        waist = waist.trim(),
                                        hips = hips.trim(),
                                        sleeveLength = sleeveLength.trim(),
                                        armCircumference = armCircumference.trim(),
                                        wrist = wrist.trim(),
                                        neck = neck.trim(),
                                        topLength = topLength.trim(),
                                        bottomLength = bottomLength.trim(),
                                        thigh = thigh.trim(),
                                        knee = knee.trim(),
                                        ankle = ankle.trim(),
                                        totalLength = totalLength.trim(),
                                        extraNotes = measurementNotes.trim()
                                    )
                                    val built = CoutureOrderRecord(
                                        id = initialOrder?.id.orEmpty(),
                                        shopId = shopId,
                                        shopName = initialOrder?.shopName?.ifBlank { shopName } ?: shopName,
                                        orderNumber = initialOrder?.orderNumber.orEmpty(),
                                        customerFirstName = firstName.trim(),
                                        customerLastName = lastName.trim(),
                                        customerPhone = phone.trim(),
                                        modelId = if (isCustomClientModel) "" else selectedModelId,
                                        modelTitle = chosenTitle,
                                        modelImageUri = if (isCustomClientModel) (fabricPhotoUri.ifBlank { "img_prod_mode" }) else selectedModelImage,
                                        isCustomClientModel = isCustomClientModel,
                                        fabricPhotoUri = fabricPhotoUri.trim(),
                                        fabricNotes = fabricNotes.trim(),
                                        measurements = meas,
                                        status = selectedStage,
                                        appointmentAt = appointmentAt,
                                        appointmentNote = appointmentNote.trim(),
                                        totalPrice = totalPrice,
                                        advancePaid = advance,
                                        saleId = initialOrder?.saleId,
                                        tailorNotes = tailorNotes.trim(),
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
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1 : IDENTITÉ CLIENT
                SectionCard(
                    title = "1. Coordonnées du Client",
                    icon = Icons.Default.Person
                ) {
                    if (customers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCustomerPicker = !showCustomerPicker },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (showCustomerPicker) "Masquer mes clients" else "Choisir un client enregistré (${customers.size})")
                        }
                        if (showCustomerPicker) {
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
                                            firstName = parts.getOrNull(0).orEmpty()
                                            lastName = parts.getOrNull(1).orEmpty()
                                            phone = cust.phone
                                            showCustomerPicker = false
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
                            value = firstName,
                            onValueChange = { firstName = it; validationError = null },
                            label = { Text("Prénom *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_couture_firstname")
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it; validationError = null },
                            label = { Text("Nom *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_couture_lastname")
                        )
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; validationError = null },
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

                // SECTION 2 : MODÈLE CHOISI OU APPORTÉ PAR LE CLIENT
                SectionCard(
                    title = "2. Modèle de Tenue",
                    icon = Icons.Default.Checkroom
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isCustomClientModel,
                            onClick = { isCustomClientModel = false },
                            label = { Text("Catalogue Atelier", fontWeight = FontWeight.Bold) },
                            leadingIcon = if (!isCustomClientModel) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isCustomClientModel,
                            onClick = { isCustomClientModel = true },
                            label = { Text("Modèle apporté par client", fontWeight = FontWeight.Bold) },
                            leadingIcon = if (isCustomClientModel) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isCustomClientModel && models.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            models.forEach { mod ->
                                val isSel = mod.id == selectedModelId
                                Surface(
                                    onClick = {
                                        selectedModelId = mod.id
                                        customModelTitle = mod.title
                                        selectedModelImage = mod.imageUri
                                        if (priceText.isBlank() || priceText == "0" || initialOrder == null) {
                                            priceText = mod.price.toString()
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
                            value = customModelTitle,
                            onValueChange = { customModelTitle = it },
                            label = { Text("Nom / description du modèle souhaité") },
                            placeholder = { Text("Ex: Robe soirée fendue + veste cintrée") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // SECTION 3 : PHOTO DIRECTE DU TISSU RÉCUPÉRÉ
                SectionCard(
                    title = "3. Photo du Tissu Récupéré",
                    icon = Icons.Default.CameraAlt
                ) {
                    CouturePhotoCaptureControl(
                        title = "Tissu remis par le client",
                        subtitle = "Prenez une photo directe du pagne / bazin / tissu pour le retrouver et le montrer au client dans son espace",
                        currentPhotoUri = fabricPhotoUri,
                        onPhotoSelected = { uri -> fabricPhotoUri = uri },
                        filePrefix = "tissu",
                        cameraButtonTestTag = "btn_couture_take_fabric_photo",
                        galleryButtonTestTag = "btn_couture_pick_fabric_gallery"
                    )
                    OutlinedTextField(
                        value = fabricNotes,
                        onValueChange = { fabricNotes = it },
                        label = { Text("Notes sur le tissu (métrage, couleur, doublure...)") },
                        placeholder = { Text("Ex: 3 pagnes Bazin blanc + fil or, doublure coton") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // SECTION 4 : OUTILS DE PRISE DE MESURES (CM)
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
                                selected = bodyProfile == prof,
                                onClick = { bodyProfile = prof },
                                label = { Text(prof, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    MeasurementRow(
                        labelA = "Épaules (cm)", valueA = shoulders, onChangeA = { shoulders = it },
                        labelB = "Poitrine / Buste", valueB = chest, onChangeB = { chest = it }
                    )
                    MeasurementRow(
                        labelA = "Tour de taille", valueA = waist, onChangeA = { waist = it },
                        labelB = "Tour de hanches", valueB = hips, onChangeB = { hips = it }
                    )
                    MeasurementRow(
                        labelA = "Long. manches", valueA = sleeveLength, onChangeA = { sleeveLength = it },
                        labelB = "Tour de bras", valueB = armCircumference, onChangeB = { armCircumference = it }
                    )
                    MeasurementRow(
                        labelA = "Poignet", valueA = wrist, onChangeA = { wrist = it },
                        labelB = "Encolure / Col", valueB = neck, onChangeB = { neck = it }
                    )
                    MeasurementRow(
                        labelA = "Long. haut / boubou", valueA = topLength, onChangeA = { topLength = it },
                        labelB = "Long. pantalon/jupe", valueB = bottomLength, onChangeB = { bottomLength = it }
                    )
                    MeasurementRow(
                        labelA = "Tour de cuisse", valueA = thigh, onChangeA = { thigh = it },
                        labelB = "Tour de genou", valueB = knee, onChangeB = { knee = it }
                    )
                    MeasurementRow(
                        labelA = "Bas / Cheville", valueA = ankle, onChangeA = { ankle = it },
                        labelB = "Long. totale robe", valueB = totalLength, onChangeB = { totalLength = it }
                    )

                    OutlinedTextField(
                        value = measurementNotes,
                        onValueChange = { measurementNotes = it },
                        label = { Text("Précisions de coupe & morphologie") },
                        placeholder = { Text("Ex: Pince poitrine prononcée, col mao 4cm, taille ajustée") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // SECTION 5 : RENDEZ-VOUS DE RÉCUPÉRATION & TARIF
                SectionCard(
                    title = "5. Rendez-vous, Tarif & Étape",
                    icon = Icons.Default.EventAvailable
                ) {
                    Surface(
                        onClick = { openDateAndTimePicker() },
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
                                        text = apptFormatter.format(Date(appointmentAt)),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF3B0764)
                                    )
                                }
                            }
                            Text("Modifier", fontWeight = FontWeight.Bold, color = CoutureViolet, fontSize = 12.sp)
                        }
                    }

                    // Raccourcis rapides de délai de livraison
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3 to "+3 jours", 5 to "+5 jours", 7 to "+7 jours", 14 to "+14 jours").forEach { (days, label) ->
                            AssistChip(
                                onClick = {
                                    appointmentAt = System.currentTimeMillis() + days.toLong() * 86_400_000L
                                },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = appointmentNote,
                        onValueChange = { appointmentNote = it },
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
                            value = priceText,
                            onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Prix confection ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = advanceText,
                            onValueChange = { advanceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Avance reçue") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val totalP = priceText.toLongOrNull() ?: 0L
                    val advP = advanceText.toLongOrNull() ?: 0L
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
                                selected = selectedStage == st,
                                onClick = { selectedStage = st },
                                label = { Text("${st.progressPercent}% ${st.shortLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tailorNotes,
                        onValueChange = { tailorNotes = it },
                        label = { Text("Message atelier visible dans le suivi client") },
                        placeholder = { Text("Ex: Broderie dorée validée avec le client") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
