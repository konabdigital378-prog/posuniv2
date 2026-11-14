package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.ShopProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

enum class CoutureStage(
    val stepIndex: Int,
    val progressPercent: Int,
    val label: String,
    val shortLabel: String,
    val description: String
) {
    DEMANDE_CLIENT(0, 10, "Modèle choisi (En attente mesures)", "DEMANDE", "Modèle sélectionné par le client via catalogue QR"),
    MESURES_PRISES(1, 25, "Mesures & tissu enregistrés", "MESURES", "Mensurations prises et photo du tissu sauvegardée"),
    COUPE_EN_COURS(2, 45, "Coupe du tissu en cours", "COUPE", "Patronage et découpe du tissu à l'atelier"),
    COUTURE_ASSEMBLAGE(3, 68, "Couture & assemblage", "COUTURE", "Montage de la tenue sur machine"),
    BRODERIE_FINITIONS(4, 85, "Broderie & finitions", "FINITIONS", "Broderie, boutons, repassage et contrôle qualité"),
    ESSAYAGE_PRET(5, 95, "Prêt pour essayage / retrait", "PRÊT", "Tenue terminée : le client peut venir récupérer"),
    LIVRE_ENCAISSE(6, 100, "Récupéré & soldé", "LIVRÉ", "Tenue remise au client et solde encaissé");

    val isOpenWork: Boolean
        get() = this != LIVRE_ENCAISSE

    fun nextStage(): CoutureStage? = when (this) {
        DEMANDE_CLIENT -> MESURES_PRISES
        MESURES_PRISES -> COUPE_EN_COURS
        COUPE_EN_COURS -> COUTURE_ASSEMBLAGE
        COUTURE_ASSEMBLAGE -> BRODERIE_FINITIONS
        BRODERIE_FINITIONS -> ESSAYAGE_PRET
        ESSAYAGE_PRET -> LIVRE_ENCAISSE
        LIVRE_ENCAISSE -> null
    }

    companion object {
        fun fromString(raw: String?): CoutureStage {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MESURES_PRISES
        }
    }
}

data class CoutureMeasurements(
    val bodyProfile: String = "Femme", // Homme, Femme, Enfant
    val shoulders: String = "",        // Épaules (cm)
    val chest: String = "",            // Tour de poitrine / buste
    val waist: String = "",            // Tour de taille
    val hips: String = "",             // Tour de hanches / bassin
    val sleeveLength: String = "",     // Longueur manche / bras
    val armCircumference: String = "", // Tour de bras
    val wrist: String = "",            // Poignet
    val neck: String = "",             // Encolure / Col
    val topLength: String = "",        // Longueur haut / tunique / boubou
    val bottomLength: String = "",     // Longueur pantalon / jupe
    val thigh: String = "",            // Tour de cuisse
    val knee: String = "",             // Tour de genou
    val ankle: String = "",            // Bas de pied / cheville
    val totalLength: String = "",      // Longueur totale robe / complet
    val extraNotes: String = ""        // Précisions de coupe
) {
    fun nonEmptyEntries(): List<Pair<String, String>> {
        val raw = listOf(
            "Épaules" to shoulders,
            "Poitrine" to chest,
            "Taille" to waist,
            "Hanches" to hips,
            "Manches" to sleeveLength,
            "Tour bras" to armCircumference,
            "Poignet" to wrist,
            "Encolure / Col" to neck,
            "Long. haut" to topLength,
            "Long. pantalon/jupe" to bottomLength,
            "Cuisse" to thigh,
            "Genou" to knee,
            "Bas / Cheville" to ankle,
            "Long. totale" to totalLength
        )
        return raw.mapNotNull { (label, valStr) ->
            val clean = valStr.trim()
            if (clean.isNotBlank()) label to (if (clean.endsWith("cm", ignoreCase = true)) clean else "$clean cm") else null
        }
    }

    fun compactSummary(): String {
        val entries = nonEmptyEntries()
        if (entries.isEmpty()) return "Mesures non saisies"
        return entries.take(5).joinToString(" • ") { "${it.first}: ${it.second}" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("body_profile", bodyProfile)
        put("shoulders", shoulders)
        put("chest", chest)
        put("waist", waist)
        put("hips", hips)
        put("sleeve_length", sleeveLength)
        put("arm_circumference", armCircumference)
        put("wrist", wrist)
        put("neck", neck)
        put("top_length", topLength)
        put("bottom_length", bottomLength)
        put("thigh", thigh)
        put("knee", knee)
        put("ankle", ankle)
        put("total_length", totalLength)
        put("extra_notes", extraNotes)
    }

    companion object {
        fun fromJsonString(raw: String?): CoutureMeasurements {
            if (raw.isNullOrBlank()) return CoutureMeasurements()
            return try {
                fromJson(JSONObject(raw))
            } catch (_: Exception) {
                CoutureMeasurements()
            }
        }

        fun fromJson(o: JSONObject): CoutureMeasurements {
            return CoutureMeasurements(
                bodyProfile = o.optString("body_profile", "Femme").trim().ifBlank { "Femme" },
                shoulders = o.optString("shoulders", "").trim(),
                chest = o.optString("chest", "").trim(),
                waist = o.optString("waist", "").trim(),
                hips = o.optString("hips", "").trim(),
                sleeveLength = o.optString("sleeve_length", "").trim(),
                armCircumference = o.optString("arm_circumference", "").trim(),
                wrist = o.optString("wrist", "").trim(),
                neck = o.optString("neck", "").trim(),
                topLength = o.optString("top_length", "").trim(),
                bottomLength = o.optString("bottom_length", "").trim(),
                thigh = o.optString("thigh", "").trim(),
                knee = o.optString("knee", "").trim(),
                ankle = o.optString("ankle", "").trim(),
                totalLength = o.optString("total_length", "").trim(),
                extraNotes = o.optString("extra_notes", "").trim()
            )
        }
    }
}

data class CoutureModelRecord(
    val id: String,
    val shopId: Long = 0L,
    val shopName: String = "",
    val title: String,
    val category: String = "Tenue sur-mesure",
    val description: String = "",
    val price: Long = 0L,
    val estimatedDays: Int = 5,
    val imageUri: String = "img_prod_mode",
    val galleryImagesJson: String = "[]",
    val isFeatured: Boolean = true,
    val isAvailable: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getGalleryList(): List<String> {
        val list = mutableListOf<String>()
        if (imageUri.isNotBlank()) list.add(imageUri.trim())
        if (galleryImagesJson.isNotBlank() && galleryImagesJson != "[]") {
            try {
                val arr = JSONArray(galleryImagesJson)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank() && !list.contains(item)) {
                        list.add(item)
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("shop_name", shopName)
        put("title", title)
        put("category", category)
        put("description", description)
        put("price", price)
        put("estimated_days", estimatedDays)
        put("image_uri", imageUri)
        put("gallery_images_json", galleryImagesJson)
        put("is_featured", isFeatured)
        put("is_available", isAvailable)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): CoutureModelRecord {
            return CoutureModelRecord(
                id = o.optString("id", "MOD-${System.currentTimeMillis()}"),
                shopId = o.optLong("shop_id", 0L),
                shopName = o.optString("shop_name", "").trim(),
                title = o.optString("title", "Modèle Couture").trim().ifBlank { "Modèle Couture" },
                category = o.optString("category", "Tenue sur-mesure").trim().ifBlank { "Tenue sur-mesure" },
                description = o.optString("description", "").trim(),
                price = o.optLong("price", 0L).coerceAtLeast(0L),
                estimatedDays = o.optInt("estimated_days", 5).coerceIn(1, 90),
                imageUri = o.optString("image_uri", "img_prod_mode").trim().ifBlank { "img_prod_mode" },
                galleryImagesJson = o.optString("gallery_images_json", "[]"),
                isFeatured = o.optBoolean("is_featured", true),
                isAvailable = o.optBoolean("is_available", true),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

data class CoutureOrderRecord(
    val id: String,
    val shopId: Long = 0L,
    val shopName: String = "",
    val orderNumber: String,
    val customerFirstName: String = "",
    val customerLastName: String = "",
    val customerPhone: String = "",
    val modelId: String = "",
    val modelTitle: String = "",
    val modelImageUri: String = "img_prod_mode",
    val isCustomClientModel: Boolean = false,
    val fabricPhotoUri: String = "",
    val fabricNotes: String = "",
    val measurements: CoutureMeasurements = CoutureMeasurements(),
    val status: CoutureStage = CoutureStage.MESURES_PRISES,
    val appointmentAt: Long = 0L,
    val appointmentNote: String = "",
    val totalPrice: Long = 0L,
    val advancePaid: Long = 0L,
    val saleId: Long? = null,
    val tailorNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val fullCustomerName: String
        get() = listOf(customerFirstName.trim(), customerLastName.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Client Atelier" }

    val remainingBalance: Long
        get() = (totalPrice - advancePaid).coerceAtLeast(0L)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("shop_name", shopName)
        put("order_number", orderNumber)
        put("customer_first_name", customerFirstName)
        put("customer_last_name", customerLastName)
        put("customer_name", fullCustomerName)
        put("customer_phone", customerPhone)
        put("model_id", modelId)
        put("model_title", modelTitle)
        put("model_image_uri", modelImageUri)
        put("is_custom_client_model", isCustomClientModel)
        put("fabric_photo_uri", fabricPhotoUri)
        put("fabric_notes", fabricNotes)
        put("measurements_json", measurements.toJson().toString())
        put("status", status.name)
        put("appointment_at", appointmentAt)
        put("appointment_note", appointmentNote)
        put("total_price", totalPrice)
        put("advance_paid", advancePaid)
        if (saleId != null) put("sale_id", saleId)
        put("tailor_notes", tailorNotes)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): CoutureOrderRecord {
            val rawFull = o.optString("customer_name", "").trim()
            val first = o.optString("customer_first_name", "").trim().ifBlank {
                rawFull.split(" ").firstOrNull().orEmpty()
            }
            val last = o.optString("customer_last_name", "").trim().ifBlank {
                rawFull.split(" ").drop(1).joinToString(" ")
            }
            val parsedSaleId = if (o.has("sale_id") && !o.isNull("sale_id")) {
                o.optLong("sale_id", 0L).takeIf { it > 0L }
            } else null
            return CoutureOrderRecord(
                id = o.optString("id", "CTRO-${System.currentTimeMillis()}"),
                shopId = o.optLong("shop_id", 0L),
                shopName = o.optString("shop_name", "").trim(),
                orderNumber = o.optString("order_number", "CTR-001"),
                customerFirstName = first,
                customerLastName = last,
                customerPhone = o.optString("customer_phone", "").trim(),
                modelId = o.optString("model_id", "").trim(),
                modelTitle = o.optString("model_title", "Tenue sur-mesure").trim().ifBlank { "Tenue sur-mesure" },
                modelImageUri = o.optString("model_image_uri", "img_prod_mode").trim(),
                isCustomClientModel = o.optBoolean("is_custom_client_model", false),
                fabricPhotoUri = o.optString("fabric_photo_uri", "").trim(),
                fabricNotes = o.optString("fabric_notes", "").trim(),
                measurements = CoutureMeasurements.fromJsonString(o.optString("measurements_json", "{}")),
                status = CoutureStage.fromString(o.optString("status", CoutureStage.MESURES_PRISES.name)),
                appointmentAt = o.optLong("appointment_at", 0L),
                appointmentNote = o.optString("appointment_note", "").trim(),
                totalPrice = o.optLong("total_price", 0L).coerceAtLeast(0L),
                advancePaid = o.optLong("advance_paid", 0L).coerceAtLeast(0L),
                saleId = parsedSaleId,
                tailorNotes = o.optString("tailor_notes", "").trim(),
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

data class CoutureClientAccount(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val city: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Client" }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("first_name", firstName)
        put("last_name", lastName)
        put("phone", phone)
        put("city", city)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): CoutureClientAccount {
            return CoutureClientAccount(
                id = o.optString("id", "CLIENT-${System.currentTimeMillis()}"),
                firstName = o.optString("first_name", "").trim(),
                lastName = o.optString("last_name", "").trim(),
                phone = o.optString("phone", "").trim(),
                city = o.optString("city", "").trim(),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

data class PublicCouturierListing(
    val shopId: Long,
    val name: String,
    val slogan: String = "",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val currency: String = "FCFA",
    val logoUri: String? = null,
    val businessType: String = "Couture",
    val modelsCount: Int = 0
) {
    fun toWebSlug(): String {
        val decomposed = Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val slug = decomposed.replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        return slug.ifBlank { "atelier-couture" }
    }
}

data class ParsedCoutureQr(
    val shopId: Long,
    val shopName: String,
    val modelId: String? = null
)

object CoutureQrParser {
    private const val SCHEME_PREFIX = "konabpos://couture"

    fun buildCatalogQrUri(
        shopId: Long,
        shopName: String,
        modelId: String? = null
    ): String {
        val cleanShop = shopName.trim().take(48)
        val encShop = URLEncoder.encode(cleanShop, "UTF-8")
        val base = "$SCHEME_PREFIX?shopId=$shopId&shop=$encShop"
        return if (!modelId.isNullOrBlank()) {
            "$base&modelId=${URLEncoder.encode(modelId.trim(), "UTF-8")}"
        } else {
            "$base&mode=catalog"
        }
    }

    fun parseCoutureQrUri(raw: String?): ParsedCoutureQr? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null

        val lower = clean.lowercase(Locale.ROOT)
        val queryPart = when {
            lower.startsWith(SCHEME_PREFIX) -> {
                val idx = clean.indexOf('?')
                if (idx >= 0) clean.substring(idx + 1) else ""
            }
            (lower.contains("konab") || lower.contains("couture")) &&
                (lower.contains("shopid=") || lower.contains("shop=") || lower.contains("modelid=")) -> {
                val idx = clean.indexOf('?')
                if (idx >= 0) clean.substring(idx + 1) else clean
            }
            else -> return null
        }

        if (queryPart.isBlank()) return null
        val params = mutableMapOf<String, String>()
        queryPart.split("&").forEach { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val k = pair.substring(0, eqIdx).trim()
                val v = try {
                    URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(eqIdx + 1)
                }
                params[k.lowercase(Locale.ROOT)] = v.trim()
            }
        }

        val shopId = (params["shopid"] ?: params["s"])?.toLongOrNull() ?: 0L
        val shopName = (params["shop"] ?: params["n"]).orEmpty()
        val modelId = (params["modelid"] ?: params["m"])?.takeIf { it.isNotBlank() }
        if (shopId <= 0L && shopName.isBlank()) return null
        return ParsedCoutureQr(
            shopId = shopId,
            shopName = shopName,
            modelId = modelId
        )
    }
}

object CoutureDirectoryHelper {
    private val COUTURE_KEYWORDS = listOf("couture", "stylis", "atelier", "tailleur", "confection", "broderie", "mode sur mesure")

    fun isCoutureShop(businessType: String?): Boolean {
        val clean = businessType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (clean.isBlank()) return false
        return clean == "couture" || COUTURE_KEYWORDS.any { clean.contains(it) }
    }

    fun normalizePhone(rawPhone: String?): String {
        return rawPhone.orEmpty().filter { it.isDigit() }
    }

    fun matchesClientOrder(order: CoutureOrderRecord, clientPhone: String?, clientName: String? = null): Boolean {
        val cleanPhone = normalizePhone(clientPhone)
        val orderPhone = normalizePhone(order.customerPhone)
        if (cleanPhone.isNotBlank() && orderPhone.isNotBlank()) {
            // Compare full digits or last 8 digits to tolerate international prefix differences (+226 / 00226 / local)
            val tailA = if (cleanPhone.length > 8) cleanPhone.takeLast(8) else cleanPhone
            val tailB = if (orderPhone.length > 8) orderPhone.takeLast(8) else orderPhone
            if (tailA == tailB) return true
        }
        val cleanName = clientName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (cleanName.length >= 3 && order.fullCustomerName.lowercase(Locale.ROOT).contains(cleanName)) {
            return true
        }
        return false
    }

    fun mergeAndFilterDirectory(
        remoteListings: List<PublicCouturierListing>,
        cachedShops: List<ShopProfileEntity> = emptyList(),
        connectedShops: List<ConnectedShopSession> = emptyList(),
        localShop: ShopProfileEntity? = null,
        localShopCloudId: Long = 0L,
        modelCountsByShopId: Map<Long, Int> = emptyMap(),
        query: String = ""
    ): List<PublicCouturierListing> {
        val byKey = LinkedHashMap<String, PublicCouturierListing>()

        fun putIfValid(item: PublicCouturierListing) {
            if (item.name.isBlank() || !isCoutureShop(item.businessType)) return
            val normName = AgentDeploymentStorage.normalizeShopName(item.name)
            val existing = byKey[normName]
            val enrichedCount = maxOf(item.modelsCount, modelCountsByShopId[item.shopId] ?: 0)
            val candidate = item.copy(modelsCount = enrichedCount)
            if (existing == null ||
                (existing.shopId <= 1L && candidate.shopId > 1L) ||
                (existing.logoUri.isNullOrBlank() && !candidate.logoUri.isNullOrBlank()) ||
                candidate.modelsCount > existing.modelsCount
            ) {
                byKey[normName] = candidate
            }
        }

        remoteListings.forEach { putIfValid(it) }

        connectedShops.forEach { s ->
            putIfValid(
                PublicCouturierListing(
                    shopId = s.cloudShopId,
                    name = s.shopName,
                    slogan = s.slogan,
                    city = s.city,
                    address = s.address,
                    phone = s.phone,
                    currency = s.currency.ifBlank { "FCFA" },
                    logoUri = s.logoUri,
                    businessType = s.businessType
                )
            )
        }

        cachedShops.forEach { s ->
            putIfValid(
                PublicCouturierListing(
                    shopId = s.id.toLong().takeIf { it > 1L } ?: 0L,
                    name = s.name,
                    slogan = s.slogan,
                    city = s.city,
                    address = s.address,
                    phone = s.phone,
                    currency = s.currency.ifBlank { "FCFA" },
                    logoUri = s.logoUri,
                    businessType = s.businessType
                )
            )
        }

        if (localShop != null && localShop.isConfigured) {
            putIfValid(
                PublicCouturierListing(
                    shopId = localShopCloudId.takeIf { it > 1L } ?: localShop.id.toLong(),
                    name = localShop.name,
                    slogan = localShop.slogan,
                    city = localShop.city,
                    address = localShop.address,
                    phone = localShop.phone,
                    currency = localShop.currency.ifBlank { "FCFA" },
                    logoUri = localShop.logoUri,
                    businessType = localShop.businessType
                )
            )
        }

        val cleanQuery = query.trim()
        return byKey.values
            .filter { item ->
                cleanQuery.isBlank() ||
                    item.name.contains(cleanQuery, ignoreCase = true) ||
                    item.city.contains(cleanQuery, ignoreCase = true) ||
                    item.slogan.contains(cleanQuery, ignoreCase = true) ||
                    item.address.contains(cleanQuery, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}

object CoutureStorage {
    private const val PREFS_NAME = "konab_couture_sync_prefs"
    private const val KEY_CLIENT_ACCOUNT = "couture_client_account"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun shopKey(prefix: String, shopId: Long): String = "${prefix}_${shopId.coerceAtLeast(0L)}"

    // --- MODELS (Modèles & Catalogues de couture) ---

    @Synchronized
    fun getModels(context: Context, shopId: Long): List<CoutureModelRecord> {
        val raw = prefs(context).getString(shopKey("models", shopId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { CoutureModelRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveModels(context: Context, shopId: Long, models: List<CoutureModelRecord>) {
        val arr = JSONArray()
        models.distinctBy { it.id }.forEach { arr.put(it.copy(shopId = shopId).toJson()) }
        prefs(context).edit().putString(shopKey("models", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun upsertModel(context: Context, shopId: Long, model: CoutureModelRecord): List<CoutureModelRecord> {
        val current = getModels(context, shopId).toMutableList()
        val norm = model.copy(shopId = shopId, updatedAt = System.currentTimeMillis())
        val idx = current.indexOfFirst { it.id == norm.id }
        if (idx >= 0) current[idx] = norm else current.add(0, norm)
        saveModels(context, shopId, current)
        return current.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun deleteModel(context: Context, shopId: Long, modelId: String): List<CoutureModelRecord> {
        val updated = getModels(context, shopId).filterNot { it.id == modelId }
        saveModels(context, shopId, updated)
        return updated
    }

    @Synchronized
    fun mergeModelsFromCloud(context: Context, shopId: Long, remoteModels: List<CoutureModelRecord>): List<CoutureModelRecord> {
        val byId = LinkedHashMap<String, CoutureModelRecord>()
        getModels(context, shopId).forEach { byId[it.id] = it }
        remoteModels.forEach { remote ->
            val existing = byId[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                byId[remote.id] = remote.copy(shopId = shopId)
            }
        }
        val finalList = byId.values.sortedByDescending { it.updatedAt }
        saveModels(context, shopId, finalList)
        return finalList
    }

    @Synchronized
    fun ensureDefaultModelsForAtelier(
        context: Context,
        shopId: Long,
        shopName: String
    ): List<CoutureModelRecord> {
        val existing = getModels(context, shopId)
        if (existing.isNotEmpty()) return existing
        val now = System.currentTimeMillis()
        val defaults = listOf(
            CoutureModelRecord(
                id = "MOD-$shopId-1",
                shopId = shopId,
                shopName = shopName,
                title = "Boubou Bazin Brodé Prestige",
                category = "Confections Hommes",
                description = "Grand boubou 3 pièces avec broderie fine au col et aux manches.",
                price = 25000L,
                estimatedDays = 7,
                imageUri = "img_prod_mode",
                isFeatured = true,
                updatedAt = now
            ),
            CoutureModelRecord(
                id = "MOD-$shopId-2",
                shopId = shopId,
                shopName = shopName,
                title = "Robe Moderne Pagne Faso Danfani",
                category = "Confections Dames",
                description = "Robe cintrée élégante avec plis structurés et finition soirée.",
                price = 18000L,
                estimatedDays = 5,
                imageUri = "burkina_bobo_dioulasso",
                isFeatured = true,
                updatedAt = now - 1000L
            ),
            CoutureModelRecord(
                id = "MOD-$shopId-3",
                shopId = shopId,
                shopName = shopName,
                title = "Ensemble Chemise & Pantalon Sur-Mesure",
                category = "Confections Hommes",
                description = "Coupe droite moderne, col officier ou classique selon tissu.",
                price = 15000L,
                estimatedDays = 4,
                imageUri = "burkina_commerce_faso",
                isFeatured = true,
                updatedAt = now - 2000L
            ),
            CoutureModelRecord(
                id = "MOD-$shopId-4",
                shopId = shopId,
                shopName = shopName,
                title = "Tenue Cérémonie Bazin Femme",
                category = "Tenues Cérémonie",
                description = "Taille haute perlée ou brodée sur mesure pour mariages et fêtes.",
                price = 22000L,
                estimatedDays = 6,
                imageUri = "img_prod_mode",
                isFeatured = true,
                updatedAt = now - 3000L
            )
        )
        saveModels(context, shopId, defaults)
        return defaults
    }

    // --- ORDERS / FITTINGS (Mesures, Photos Tissus, Rendez-vous & Suivi Progression) ---

    @Synchronized
    fun getOrders(context: Context, shopId: Long): List<CoutureOrderRecord> {
        val raw = prefs(context).getString(shopKey("orders", shopId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { CoutureOrderRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun getAllLocalOrders(context: Context): List<CoutureOrderRecord> {
        val allPrefs = prefs(context).all
        val out = LinkedHashMap<String, CoutureOrderRecord>()
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("orders_") && value is String) {
                try {
                    val arr = JSONArray(value)
                    for (i in 0 until arr.length()) {
                        val rec = CoutureOrderRecord.fromJson(arr.getJSONObject(i))
                        val existing = out[rec.id]
                        if (existing == null || rec.updatedAt >= existing.updatedAt) {
                            out[rec.id] = rec
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return out.values.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun saveOrders(context: Context, shopId: Long, orders: List<CoutureOrderRecord>) {
        val arr = JSONArray()
        orders.sortedByDescending { it.updatedAt }.take(300).forEach {
            arr.put(it.copy(shopId = shopId).toJson())
        }
        prefs(context).edit().putString(shopKey("orders", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun upsertOrder(context: Context, shopId: Long, order: CoutureOrderRecord): List<CoutureOrderRecord> {
        val current = getOrders(context, shopId).toMutableList()
        val norm = order.copy(shopId = shopId)
        val idx = current.indexOfFirst { it.id == norm.id }
        if (idx >= 0) {
            if (norm.updatedAt >= current[idx].updatedAt) {
                current[idx] = norm
            }
        } else {
            current.add(0, norm)
        }
        saveOrders(context, shopId, current)
        return current.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun mergeOrdersFromCloud(context: Context, shopId: Long, remoteOrders: List<CoutureOrderRecord>): List<CoutureOrderRecord> {
        val byId = LinkedHashMap<String, CoutureOrderRecord>()
        getOrders(context, shopId).forEach { byId[it.id] = it }
        remoteOrders.forEach { remote ->
            val existing = byId[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                byId[remote.id] = remote.copy(shopId = shopId)
            }
        }
        val finalList = byId.values.sortedByDescending { it.updatedAt }
        saveOrders(context, shopId, finalList)
        return finalList
    }

    @Synchronized
    fun nextOrderNumber(context: Context, shopId: Long): String {
        val count = getOrders(context, shopId).size + 1
        return "CTR-${count.toString().padStart(3, '0')}"
    }

    // --- PUBLIC MODELS CACHE & CLIENT ACCOUNT ---

    @Synchronized
    fun getAllLocalModels(context: Context): List<CoutureModelRecord> {
        val allPrefs = prefs(context).all
        val out = LinkedHashMap<String, CoutureModelRecord>()
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("models_") && value is String) {
                try {
                    val arr = JSONArray(value)
                    for (i in 0 until arr.length()) {
                        val rec = CoutureModelRecord.fromJson(arr.getJSONObject(i))
                        if (rec.isAvailable) {
                            val existing = out[rec.id]
                            if (existing == null || rec.updatedAt >= existing.updatedAt) {
                                out[rec.id] = rec
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return out.values.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun saveClientAccount(context: Context, account: CoutureClientAccount) {
        prefs(context).edit().putString(KEY_CLIENT_ACCOUNT, account.toJson().toString()).apply()
    }

    @Synchronized
    fun getClientAccount(context: Context): CoutureClientAccount? {
        val raw = prefs(context).getString(KEY_CLIENT_ACCOUNT, null) ?: return null
        return try {
            val acc = CoutureClientAccount.fromJson(JSONObject(raw))
            if (acc.phone.isBlank() && acc.firstName.isBlank()) null else acc
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clearClientAccount(context: Context) {
        prefs(context).edit().remove(KEY_CLIENT_ACCOUNT).apply()
    }
}
