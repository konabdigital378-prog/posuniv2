package com.example.data.remote.pocketbase

import android.content.Context
import android.util.Base64
import com.example.data.remote.supabase.SupabaseAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/**
 * Adaptateur complet PocketBase (v0.23+) pour Konab POS :
 * - Traduction transparente des requêtes REST PostgREST vers `/api/collections/<name>/records`
 * - Routage automatique vers les vues SQLite allégées (`v_shop_directory_light`, `v_shop_ads_feed_light`,
 *   `v_couture_models_public_light`, `v_app_users_public_safe`)
 * - Authentification native `app_users` (+ fallback `_superusers`)
 * - Upload multipart vers la collection `media_assets` avec URL publique `/api/files/...`
 */
object PocketBaseAdapter {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val numericFilterFields = setOf(
        "shop_id",
        "sale_id",
        "product_id",
        "customer_id",
        "supplier_id",
        "reference_id",
        "timestamp",
        "updated_at",
        "created_at",
        "sell_price",
        "buy_price",
        "stock_quantity",
        "min_stock_alert",
        "total_amount",
        "subtotal",
        "discount_amount",
        "amount_paid",
        "debt_amount",
        "amount",
        "daily_amount",
        "total_days",
        "target_passages",
        "video_duration_sec",
        "estimated_days",
        "sort_order",
        "seats",
        "duration_days"
    )

    private val shopSensitiveColumns = setOf(
        "admin_pin",
        "cashier_pin",
        "admin_phone",
        "admin_name",
        "admin_email",
        "email",
        "user_id",
        "owner_id"
    )

    private val fileUriPairs = listOf(
        "logo_file" to "logo_uri",
        "image_file" to "image_uri",
        "video_file" to "video_uri",
        "photo_file" to "photo_uri",
        "fabric_photo_file" to "fabric_photo_uri"
    )

    private data class ParsedPostgrestQuery(
        val table: String,
        val selectCols: List<String>,
        val isWildcardSelect: Boolean,
        val orderClauses: List<String>,
        val limit: Int,
        val filters: List<String>,
        val onConflict: String?
    )

    private fun buildSyntheticResponse(
        url: String,
        code: Int,
        body: String
    ): Response {
        val req = Request.Builder().url(url).build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "HTTP $code")
            .body(body.toResponseBody(jsonMediaType))
            .build()
    }

    private fun encodeUrlParam(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun escapePbFilterString(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun parseEndpoint(endpoint: String): ParsedPostgrestQuery {
        val split = endpoint.split("?", limit = 2)
        val table = split[0].trim().trim('/')
        val rawQuery = split.getOrNull(1).orEmpty()

        var selectRaw = "*"
        val orderClauses = mutableListOf<String>()
        var limit = 500
        val filters = mutableListOf<String>()
        var onConflict: String? = null

        if (rawQuery.isNotBlank()) {
            rawQuery.split("&").forEach { pair ->
                if (pair.isBlank()) return@forEach
                val kv = pair.split("=", limit = 2)
                val key = try {
                    URLDecoder.decode(kv[0], "UTF-8")
                } catch (_: Exception) {
                    kv[0]
                }.trim()
                val rawVal = kv.getOrNull(1).orEmpty()
                val decodedVal = try {
                    URLDecoder.decode(rawVal, "UTF-8")
                } catch (_: Exception) {
                    rawVal
                }.trim()

                when (key) {
                    "select" -> if (decodedVal.isNotBlank()) selectRaw = decodedVal
                    "order" -> {
                        decodedVal.split(",").forEach { clause ->
                            val trimmed = clause.trim()
                            if (trimmed.isNotBlank()) {
                                val parts = trimmed.split(".", limit = 2)
                                val col = parts[0].trim()
                                val dir = parts.getOrNull(1)?.trim()?.lowercase()
                                if (col.isNotBlank()) {
                                    orderClauses.add(if (dir == "desc") "-$col" else col)
                                }
                            }
                        }
                    }
                    "limit" -> limit = decodedVal.toIntOrNull()?.coerceIn(1, 2000) ?: 500
                    "on_conflict" -> onConflict = decodedVal.ifBlank { null }
                    else -> {
                        when {
                            decodedVal.startsWith("eq.") -> {
                                val rhs = decodedVal.removePrefix("eq.")
                                val pbExpr = when {
                                    rhs.equals("true", ignoreCase = true) -> "$key = true"
                                    rhs.equals("false", ignoreCase = true) -> "$key = false"
                                    key != "id" && key in numericFilterFields && rhs.toLongOrNull() != null ->
                                        "$key = $rhs"
                                    else ->
                                        "$key = '${escapePbFilterString(rhs)}'"
                                }
                                filters.add(pbExpr)
                            }
                            decodedVal.startsWith("ilike.") -> {
                                val rhs = decodedVal.removePrefix("ilike.").trim('%', '*')
                                if (rhs.isNotBlank()) {
                                    filters.add("$key ~ '${escapePbFilterString(rhs)}'")
                                }
                            }
                        }
                    }
                }
            }
        }

        val selectCols = if (selectRaw == "*") {
            emptyList()
        } else {
            selectRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }

        return ParsedPostgrestQuery(
            table = table,
            selectCols = selectCols,
            isWildcardSelect = selectCols.isEmpty(),
            orderClauses = orderClauses,
            limit = limit,
            filters = filters,
            onConflict = onConflict
        )
    }

    private fun resolveTargetReadCollection(
        parsed: ParsedPostgrestQuery,
        authToken: String?
    ): String {
        return when (parsed.table) {
            "shop_profile" -> {
                val asksSensitive = parsed.isWildcardSelect ||
                    parsed.selectCols.any { it in shopSensitiveColumns }
                if (!asksSensitive) {
                    "v_shop_directory_light"
                } else {
                    "shop_profile"
                }
            }
            "shop_ads" -> {
                if (parsed.filters.any { it.replace(" ", "") == "is_active=true" }) {
                    "v_shop_ads_feed_light"
                } else {
                    "shop_ads"
                }
            }
            "couture_models" -> {
                if (parsed.filters.any { it.replace(" ", "") == "is_available=true" }) {
                    "v_couture_models_public_light"
                } else {
                    "couture_models"
                }
            }
            "app_users" -> {
                if (authToken.isNullOrBlank()) "v_app_users_public_safe" else "app_users"
            }
            else -> parsed.table
        }
    }

    private fun buildPocketBaseFieldsParam(parsed: ParsedPostgrestQuery): String? {
        if (parsed.isWildcardSelect) return null
        val fields = linkedSetOf("id")
        parsed.selectCols.forEach { col ->
            fields.add(col)
            fileUriPairs.firstOrNull { it.second == col }?.first?.let { fileCol ->
                fields.add(fileCol)
            }
        }
        return fields.joinToString(",")
    }

    private fun hydratePocketBaseRecord(
        obj: JSONObject,
        collectionName: String,
        baseUrl: String
    ): JSONObject {
        val cleanBase = baseUrl.trimEnd('/')
        val recordIdStr = obj.optString("id", "").trim()
        val sourceColForFiles = when (collectionName) {
            "v_shop_directory_light" -> "shop_profile"
            "v_products_catalog_light" -> "products"
            "v_shop_ads_feed_light" -> "shop_ads"
            "v_couture_models_public_light" -> "couture_models"
            "v_restaurant_orders_live" -> "restaurant_orders"
            "v_app_users_public_safe" -> "app_users"
            else -> collectionName
        }

        for ((fileField, uriField) in fileUriPairs) {
            val fileVal = if (obj.has(fileField) && !obj.isNull(fileField)) {
                obj.optString(fileField, "").trim()
            } else ""
            val currentUri = if (obj.has(uriField) && !obj.isNull(uriField)) {
                obj.optString(uriField, "").trim()
            } else ""
            if (currentUri.isBlank() && fileVal.isNotBlank() && recordIdStr.isNotBlank() && cleanBase.isNotBlank()) {
                obj.put(uriField, "$cleanBase/api/files/$sourceColForFiles/$recordIdStr/$fileVal")
            }
        }

        // Preserve numeric shop_id on shop_profile if record id is non-numeric
        if ((collectionName == "shop_profile" || collectionName == "v_shop_directory_light") &&
            recordIdStr.toLongOrNull() == null
        ) {
            val numericShopId = obj.optLong("shop_id", 0L)
            if (numericShopId > 0L) {
                obj.put("id", numericShopId)
            }
        }

        return obj
    }

    private fun fetchCollectionItems(
        client: OkHttpClient,
        baseUrl: String,
        collection: String,
        parsed: ParsedPostgrestQuery,
        authToken: String?,
        fieldsOverride: String? = null
    ): Triple<Boolean, Int, String> {
        val cleanBase = baseUrl.trimEnd('/')
        val allItems = JSONArray()
        var page = 1
        val targetLimit = parsed.limit.coerceIn(1, 2000)

        while (allItems.length() < targetLimit) {
            val perPage = minOf(targetLimit - allItems.length(), 500)
            val qs = mutableListOf("page=$page", "perPage=$perPage")
            if (parsed.filters.isNotEmpty()) {
                val joined = parsed.filters.joinToString(" && ") { "($it)" }
                qs.add("filter=${encodeUrlParam(joined)}")
            }
            if (parsed.orderClauses.isNotEmpty()) {
                qs.add("sort=${encodeUrlParam(parsed.orderClauses.joinToString(","))}")
            }
            val fieldsStr = fieldsOverride ?: buildPocketBaseFieldsParam(parsed)
            if (!fieldsStr.isNullOrBlank()) {
                qs.add("fields=${encodeUrlParam(fieldsStr)}")
            }

            val url = "$cleanBase/api/collections/$collection/records?${qs.joinToString("&")}"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
            if (!authToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $authToken")
            }

            var totalPages = 1
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Triple(false, resp.code, rawBody)
                }
                val root = try {
                    JSONObject(rawBody)
                } catch (_: Exception) {
                    return Triple(true, 200, rawBody.ifBlank { "[]" })
                }
                val items = root.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    allItems.put(hydratePocketBaseRecord(item, collection, cleanBase))
                }
                totalPages = root.optInt("totalPages", 1).coerceAtLeast(1)
                if (items.length() == 0) {
                    return Triple(true, 200, allItems.toString())
                }
            }
            if (page >= totalPages) break
            page++
        }

        return Triple(true, 200, allItems.toString())
    }

    private fun preparePayloadForPocketBase(
        rawObj: JSONObject,
        collection: String,
        isCreate: Boolean
    ): Pair<String?, JSONObject> {
        val copy = JSONObject(rawObj.toString())

        if (collection == "products" && copy.has("images")) {
            copy.remove("images")
        }

        var recordId: String? = if (copy.has("id") && !copy.isNull("id")) {
            val raw = copy.opt("id")
            when (raw) {
                is Number -> raw.toLong().toString()
                else -> raw?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            }
        } else null

        if (collection == "shop_profile") {
            val numericId = recordId?.toLongOrNull() ?: copy.optLong("shop_id", 0L)
            if (numericId > 0L && (!copy.has("shop_id") || copy.optLong("shop_id", 0L) <= 0L)) {
                copy.put("shop_id", numericId)
            }
        }

        if (collection == "restaurant_menu_day" && recordId.isNullOrBlank()) {
            val shopId = copy.optLong("shop_id", 0L)
            if (shopId > 0L) {
                recordId = "menuday_$shopId"
            }
        }

        if (recordId != null) {
            copy.put("id", recordId)
        }

        if (isCreate && collection == "app_users") {
            if (!copy.has("password") || copy.optString("password", "").length < 6) {
                val generated = "Konab#" + UUID.randomUUID().toString().replace("-", "").take(14)
                copy.put("password", generated)
                copy.put("passwordConfirm", generated)
            } else if (!copy.has("passwordConfirm")) {
                copy.put("passwordConfirm", copy.optString("password"))
            }
            if (!copy.has("emailVisibility")) {
                copy.put("emailVisibility", true)
            }
        } else if (!isCreate) {
            // Avoid overwriting password/id during PATCH
            copy.remove("id")
            if (collection == "app_users") {
                copy.remove("password")
                copy.remove("passwordConfirm")
            }
        }

        return recordId to copy
    }

    private fun upsertSingleRecord(
        client: OkHttpClient,
        baseUrl: String,
        collection: String,
        rawObj: JSONObject,
        authToken: String?
    ): Triple<Boolean, Int, String> {
        val cleanBase = baseUrl.trimEnd('/')
        val (recordId, patchBody) = preparePayloadForPocketBase(rawObj, collection, isCreate = false)

        if (!recordId.isNullOrBlank()) {
            val patchUrl = "$cleanBase/api/collections/$collection/records/${encodeUrlParam(recordId)}"
            val patchReq = Request.Builder()
                .url(patchUrl)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                }
                .patch(patchBody.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(patchReq).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return Triple(true, resp.code, respBody)
                }
                if (resp.code != 404) {
                    return Triple(false, resp.code, respBody)
                }
            }
        }

        // Create if 404 or no ID
        val (_, createBody) = preparePayloadForPocketBase(rawObj, collection, isCreate = true)
        val postUrl = "$cleanBase/api/collections/$collection/records"
        val postReq = Request.Builder()
            .url(postUrl)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply {
                if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
            }
            .post(createBody.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(postReq).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return Triple(true, resp.code, respBody)
            }
            // Unique conflict fallback for restaurant_menu_day (shop_id) or shop_profile (shop_id/name)
            if (resp.code == 400 && collection == "restaurant_menu_day" && rawObj.optLong("shop_id", 0L) > 0L) {
                val sid = rawObj.optLong("shop_id")
                val lookup = fetchCollectionItems(
                    client = client,
                    baseUrl = cleanBase,
                    collection = collection,
                    parsed = parseEndpoint("$collection?shop_id=eq.$sid&select=id&limit=1"),
                    authToken = authToken,
                    fieldsOverride = "id"
                )
                if (lookup.first) {
                    val arr = try { JSONArray(lookup.third) } catch (_: Exception) { JSONArray() }
                    val existingId = arr.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
                    if (existingId != null) {
                        val retryPatchUrl = "$cleanBase/api/collections/$collection/records/${encodeUrlParam(existingId)}"
                        val retryReq = Request.Builder()
                            .url(retryPatchUrl)
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/json")
                            .apply {
                                if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                            }
                            .patch(patchBody.toString().toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(retryReq).execute().use { r ->
                            return Triple(r.isSuccessful, r.code, r.body?.string().orEmpty())
                        }
                    }
                }
            }
            return Triple(false, resp.code, respBody)
        }
    }

    fun executeRestShim(
        client: OkHttpClient,
        endpoint: String,
        method: String,
        bodyString: String?,
        authToken: String?
    ): Response {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            return buildSyntheticResponse(
                url = "https://pocketbase.unconfigured/api",
                code = 503,
                body = """{"message":"PocketBase non configuré. Veuillez renseigner l'URL du serveur."}"""
            )
        }

        val parsed = parseEndpoint(endpoint)
        val syntheticUrl = "$baseUrl/api/collections/${parsed.table}/records"
        val effectiveMethod = method.uppercase()

        return try {
            when (effectiveMethod) {
                "GET" -> {
                    val targetCol = resolveTargetReadCollection(parsed, authToken)
                    var res = fetchCollectionItems(
                        client = client,
                        baseUrl = baseUrl,
                        collection = targetCol,
                        parsed = parsed,
                        authToken = authToken
                    )

                    // Retry without token on auth failure (expired token) or fallback to public light view on empty unauthenticated shop_profile
                    if (!res.first && (res.second == 401 || res.second == 403) && !authToken.isNullOrBlank()) {
                        SupabaseAuthManager.clearExpiredSessionIfNeeded()
                        val publicCol = resolveTargetReadCollection(parsed, null)
                        res = fetchCollectionItems(
                            client = client,
                            baseUrl = baseUrl,
                            collection = publicCol,
                            parsed = parsed,
                            authToken = null
                        )
                    } else if (res.first &&
                        parsed.table == "shop_profile" &&
                        targetCol == "shop_profile" &&
                        authToken.isNullOrBlank()
                    ) {
                        val arr = try { JSONArray(res.third) } catch (_: Exception) { JSONArray() }
                        if (arr.length() == 0) {
                            val lightParsed = parsed.copy(isWildcardSelect = true, selectCols = emptyList())
                            res = fetchCollectionItems(
                                client = client,
                                baseUrl = baseUrl,
                                collection = "v_shop_directory_light",
                                parsed = lightParsed,
                                authToken = null
                            )
                        }
                    }

                    buildSyntheticResponse(
                        url = syntheticUrl,
                        code = res.second,
                        body = if (res.first) res.third else res.third.ifBlank { """{"message":"Erreur PocketBase"}""" }
                    )
                }

                "POST", "PATCH" -> {
                    val cleanBody = bodyString?.trim().orEmpty()
                    val itemsToSave = when {
                        cleanBody.startsWith("[") -> JSONArray(cleanBody)
                        cleanBody.startsWith("{") -> JSONArray().apply { put(JSONObject(cleanBody)) }
                        else -> JSONArray()
                    }
                    if (itemsToSave.length() == 0) {
                        return buildSyntheticResponse(syntheticUrl, 200, "[]")
                    }

                    val outArray = JSONArray()
                    for (i in 0 until itemsToSave.length()) {
                        val item = itemsToSave.optJSONObject(i) ?: continue
                        val (ok, code, respBody) = upsertSingleRecord(
                            client = client,
                            baseUrl = baseUrl,
                            collection = parsed.table,
                            rawObj = item,
                            authToken = authToken
                        )
                        if (!ok) {
                            return buildSyntheticResponse(
                                url = syntheticUrl,
                                code = code,
                                body = respBody.ifBlank { """{"message":"Échec écriture PocketBase ($code)"}""" }
                            )
                        }
                        val savedObj = try { JSONObject(respBody) } catch (_: Exception) { null }
                        if (savedObj != null) {
                            outArray.put(hydratePocketBaseRecord(savedObj, parsed.table, baseUrl))
                        }
                    }
                    buildSyntheticResponse(syntheticUrl, 200, outArray.toString())
                }

                "DELETE" -> {
                    val directId = parsed.filters
                        .firstOrNull { it.startsWith("id = ") }
                        ?.removePrefix("id = ")
                        ?.trim()
                        ?.trim('\'')

                    if (!directId.isNullOrBlank() && parsed.filters.size == 1) {
                        val delUrl = "$baseUrl/api/collections/${parsed.table}/records/${encodeUrlParam(directId)}"
                        val req = Request.Builder()
                            .url(delUrl)
                            .apply {
                                if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                            }
                            .delete()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            val b = resp.body?.string().orEmpty()
                            val okCode = if (resp.code == 404) 204 else resp.code
                            buildSyntheticResponse(delUrl, okCode, b)
                        }
                    } else {
                        val lookup = fetchCollectionItems(
                            client = client,
                            baseUrl = baseUrl,
                            collection = parsed.table,
                            parsed = parsed.copy(limit = 200),
                            authToken = authToken,
                            fieldsOverride = "id"
                        )
                        if (!lookup.first) {
                            return buildSyntheticResponse(syntheticUrl, lookup.second, lookup.third)
                        }
                        val matches = try { JSONArray(lookup.third) } catch (_: Exception) { JSONArray() }
                        for (i in 0 until matches.length()) {
                            val id = matches.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() } ?: continue
                            val delUrl = "$baseUrl/api/collections/${parsed.table}/records/${encodeUrlParam(id)}"
                            val req = Request.Builder()
                                .url(delUrl)
                                .apply {
                                    if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                                }
                                .delete()
                                .build()
                            client.newCall(req).execute().close()
                        }
                        buildSyntheticResponse(syntheticUrl, 204, "")
                    }
                }

                else -> buildSyntheticResponse(syntheticUrl, 405, """{"message":"Méthode non supportée: $effectiveMethod"}""")
            }
        } catch (e: Exception) {
            buildSyntheticResponse(
                url = syntheticUrl,
                code = 500,
                body = """{"message":"${escapePbFilterString(e.localizedMessage ?: "Erreur PocketBase")}"}"""
            )
        }
    }

    suspend fun testConnection(
        client: OkHttpClient,
        customUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetUrl = (customUrl?.trim()?.takeIf { it.isNotBlank() } ?: PocketBaseConfig.rawUrl).trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(targetUrl)) {
            return@withContext Result.failure(
                IllegalStateException("PocketBase non configuré. Renseignez l'URL complète du serveur (ex: https://mon-serveur.pockethost.io ou http://10.0.2.2:8090).")
            )
        }

        try {
            val healthReq = Request.Builder()
                .url("$targetUrl/api/health")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(healthReq).execute().use { healthResp ->
                if (!healthResp.isSuccessful) {
                    val errBody = healthResp.body?.string().orEmpty()
                    return@withContext Result.failure(
                        IOException("Serveur PocketBase injoignable (HTTP ${healthResp.code}) : $errBody")
                    )
                }
            }

            val colReq = Request.Builder()
                .url("$targetUrl/api/collections/v_shop_directory_light/records?perPage=1")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(colReq).execute().use { colResp ->
                when {
                    colResp.isSuccessful -> Result.success(
                        "Connexion PocketBase 100% réussie ! Serveur sain et schéma Konab POS (collections & vues) prêt."
                    )
                    colResp.code == 404 -> Result.success(
                        "Serveur PocketBase joignable ! Veuillez importer le fichier pb_collections.json dans Paramètres > Import collections de votre Admin UI PocketBase."
                    )
                    else -> {
                        val err = colResp.body?.string().orEmpty()
                        Result.failure(IOException("Erreur PocketBase (HTTP ${colResp.code}) : $err"))
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(IOException("Adresse PocketBase introuvable (${e.message}). Vérifiez l'URL et votre connexion Internet."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(IOException("Délai dépassé : le serveur PocketBase ne répond pas."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadToMediaAssets(
        client: OkHttpClient,
        bucket: String,
        file: File,
        remotePath: String,
        contentType: String,
        authToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(baseUrl)) {
            return@withContext Result.failure(IllegalStateException("PocketBase non configuré"))
        }
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Fichier média introuvable"))
        }

        val cleanBucket = bucket.trim().trim('/')
        val cleanPath = remotePath.trimStart('/')
        val recordId = deterministicMediaId("$cleanBucket/$cleanPath")
        val mediaType = contentType.ifBlank { "application/octet-stream" }.toMediaType()
        val uploadFileName = cleanPath.substringAfterLast('/').ifBlank { file.name }

        fun buildMultipart(includeId: Boolean): MultipartBody {
            return MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    if (includeId) addFormDataPart("id", recordId)
                    addFormDataPart("bucket", cleanBucket)
                    addFormDataPart("remote_path", cleanPath)
                    addFormDataPart("content_type", contentType)
                    addFormDataPart("file", uploadFileName, file.asRequestBody(mediaType))
                }
                .build()
        }

        try {
            val patchUrl = "$baseUrl/api/collections/media_assets/records/$recordId"
            val patchReq = Request.Builder()
                .url(patchUrl)
                .apply {
                    if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                }
                .patch(buildMultipart(includeId = false))
                .build()

            var shouldCreate = false
            client.newCall(patchReq).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val savedFile = JSONObject(respBody).optString("file", uploadFileName)
                    return@withContext Result.success(
                        "$baseUrl/api/files/media_assets/$recordId/$savedFile?v=${System.currentTimeMillis()}"
                    )
                } else if (resp.code == 404) {
                    shouldCreate = true
                } else {
                    return@withContext Result.failure(IOException("Échec upload PocketBase (${resp.code}): $respBody"))
                }
            }

            if (shouldCreate) {
                val postUrl = "$baseUrl/api/collections/media_assets/records"
                val postReq = Request.Builder()
                    .url(postUrl)
                    .apply {
                        if (!authToken.isNullOrBlank()) addHeader("Authorization", "Bearer $authToken")
                    }
                    .post(buildMultipart(includeId = true))
                    .build()

                client.newCall(postReq).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = JSONObject(respBody)
                        val actualId = obj.optString("id", recordId)
                        val savedFile = obj.optString("file", uploadFileName)
                        return@withContext Result.success(
                            "$baseUrl/api/files/media_assets/$actualId/$savedFile?v=${System.currentTimeMillis()}"
                        )
                    } else {
                        return@withContext Result.failure(IOException("Échec création média PocketBase (${resp.code}): $respBody"))
                    }
                }
            }
            Result.failure(IOException("Échec upload média PocketBase"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deterministicMediaId(key: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hex = md.digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            "pbmedia_${hex.take(20)}"
        } catch (_: Exception) {
            "pbmedia_${kotlin.math.abs(key.hashCode())}"
        }
    }

    // --- AUTH BRIDGE (app_users & _superusers fallback) ---

    private fun extractJwtExpiresAtMs(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                val expSec = JSONObject(decoded).optLong("exp", 0L)
                if (expSec > 0L) expSec * 1000L else System.currentTimeMillis() + 14L * 24L * 3600L * 1000L
            } else {
                System.currentTimeMillis() + 14L * 24L * 3600L * 1000L
            }
        } catch (_: Exception) {
            System.currentTimeMillis() + 14L * 24L * 3600L * 1000L
        }
    }

    private fun parsePocketBaseAuthJson(
        json: JSONObject,
        fallbackEmail: String,
        isSuperUser: Boolean = false
    ): SupabaseAuthManager.SupabaseAuthSession {
        val token = json.getString("token")
        val record = json.optJSONObject("record") ?: JSONObject()
        val id = record.optString("id", "pb-user")
        val email = record.optString("email", fallbackEmail).ifBlank { fallbackEmail }
        val name = record.optString("full_name", "").ifBlank {
            if (isSuperUser) "Super Admin PocketBase"
            else email.substringBefore("@").ifBlank { "Gérant" }
        }
        val role = record.optString("role", "").ifBlank {
            if (isSuperUser) "SUPERADMIN" else "ADMIN"
        }
        val phone = record.optString("phone", "")
        val created = record.optString("created", "")

        return SupabaseAuthManager.SupabaseAuthSession(
            accessToken = token,
            refreshToken = token,
            expiresAt = extractJwtExpiresAtMs(token),
            user = SupabaseAuthManager.SupabaseUser(
                id = id,
                email = email,
                name = name,
                role = role,
                phone = phone,
                createdAt = created
            )
        )
    }

    private fun parsePocketBaseError(body: String, code: Int): String {
        return try {
            val obj = JSONObject(body)
            val dataObj = obj.optJSONObject("data")
            val firstFieldErr = if (dataObj != null && dataObj.keys().hasNext()) {
                val firstKey = dataObj.keys().next()
                dataObj.optJSONObject(firstKey)?.optString("message").orEmpty()
            } else ""
            val msg = obj.optString("message", "").trim()
            when {
                firstFieldErr.isNotBlank() && msg.isNotBlank() -> "$msg ($firstFieldErr)"
                msg.isNotBlank() -> msg
                code == 400 -> "Identifiants PocketBase invalides"
                code == 401 -> "Session expirée ou non autorisée"
                code == 404 -> "Collection app_users introuvable. Importez pb_collections.json."
                else -> "Erreur PocketBase HTTP $code"
            }
        } catch (_: Exception) {
            "Erreur serveur PocketBase ($code)"
        }
    }

    suspend fun signIn(
        client: OkHttpClient,
        email: String,
        password: String
    ): Result<SupabaseAuthManager.SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(baseUrl)) {
            return@withContext Result.failure(IllegalStateException("PocketBase non configuré. Renseignez l'URL du serveur."))
        }
        val payload = JSONObject().apply {
            put("identity", email.trim())
            put("password", password.trim())
        }.toString()

        try {
            val req = Request.Builder()
                .url("$baseUrl/api/collections/app_users/auth-with-password")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            var primaryErrCode = 0
            var primaryErrBody = ""
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@withContext Result.success(parsePocketBaseAuthJson(JSONObject(body), email))
                }
                primaryErrCode = resp.code
                primaryErrBody = body
            }

            // Fallback: try PocketBase _superusers auth if user entered admin credentials
            val superReq = Request.Builder()
                .url("$baseUrl/api/collections/_superusers/auth-with-password")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(superReq).execute().use { superResp ->
                val body = superResp.body?.string().orEmpty()
                if (superResp.isSuccessful) {
                    return@withContext Result.success(
                        parsePocketBaseAuthJson(JSONObject(body), email, isSuperUser = true)
                    )
                }
            }

            Result.failure(IOException(parsePocketBaseError(primaryErrBody, primaryErrCode)))
        } catch (e: Exception) {
            Result.failure(IOException("Échec connexion PocketBase : ${e.localizedMessage ?: "Serveur injoignable"}", e))
        }
    }

    suspend fun signUp(
        client: OkHttpClient,
        email: String,
        password: String,
        name: String,
        phone: String,
        role: String,
        shopName: String
    ): Result<SupabaseAuthManager.SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(baseUrl)) {
            return@withContext Result.failure(IllegalStateException("PocketBase non configuré."))
        }
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()
        val cleanName = name.trim().ifBlank { "Gérant" }
        val cleanPhone = phone.trim().ifBlank { "00000000" }
        val now = System.currentTimeMillis()

        val createPayload = JSONObject().apply {
            put("email", cleanEmail)
            put("emailVisibility", true)
            put("password", cleanPassword)
            put("passwordConfirm", cleanPassword)
            put("full_name", cleanName)
            put("phone", cleanPhone)
            put("role", role.trim().uppercase().ifBlank { "ADMIN" })
            put("preferred_activity", "COMMERCANT")
            put("is_shop_member", shopName.isNotBlank())
            put("member_shop_name", shopName.trim())
            put("member_shop_role", role.trim().uppercase().ifBlank { "ADMIN" })
            put("created_at", now)
            put("updated_at", now)
        }.toString()

        try {
            val createReq = Request.Builder()
                .url("$baseUrl/api/collections/app_users/records")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(createPayload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(createReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException(parsePocketBaseError(body, resp.code)))
                }
            }

            // Immediately sign in to get live JWT token
            signIn(client, cleanEmail, cleanPassword)
        } catch (e: Exception) {
            Result.failure(IOException("Échec inscription PocketBase : ${e.localizedMessage ?: "Serveur injoignable"}", e))
        }
    }

    suspend fun recoverPassword(
        client: OkHttpClient,
        email: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(baseUrl)) {
            return@withContext Result.failure(IllegalStateException("PocketBase non configuré."))
        }
        val cleanEmail = email.trim()
        val payload = JSONObject().apply { put("email", cleanEmail) }.toString()
        val req = Request.Builder()
            .url("$baseUrl/api/collections/app_users/request-password-reset")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    Result.success("Un email de réinitialisation PocketBase a été envoyé à $cleanEmail")
                } else {
                    Result.failure(IOException(parsePocketBaseError(body, resp.code)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshSession(
        client: OkHttpClient,
        currentToken: String
    ): Result<SupabaseAuthManager.SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val baseUrl = PocketBaseConfig.rawUrl.trimEnd('/')
        if (!PocketBaseConfig.isValidHttpUrl(baseUrl)) {
            return@withContext Result.failure(IllegalStateException("PocketBase non configuré."))
        }
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/collections/app_users/auth-refresh")
                .addHeader("Authorization", "Bearer $currentToken")
                .addHeader("Accept", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@withContext Result.success(parsePocketBaseAuthJson(JSONObject(body), ""))
                }
            }

            val superReq = Request.Builder()
                .url("$baseUrl/api/collections/_superusers/auth-refresh")
                .addHeader("Authorization", "Bearer $currentToken")
                .addHeader("Accept", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(superReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@withContext Result.success(
                        parsePocketBaseAuthJson(JSONObject(body), "", isSuperUser = true)
                    )
                } else {
                    Result.failure(IOException(parsePocketBaseError(body, resp.code)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runSelfTest(
        client: OkHttpClient,
        currentSession: SupabaseAuthManager.SupabaseAuthSession?
    ): SupabaseAuthManager.DiagnosticResult = withContext(Dispatchers.IO) {
        val details = mutableListOf<String>()
        val startTotal = System.currentTimeMillis()
        val targetUrl = PocketBaseConfig.rawUrl.trimEnd('/')

        if (!PocketBaseConfig.isConfigured) {
            return@withContext SupabaseAuthManager.DiagnosticResult(
                isSuccessful = false,
                url = targetUrl,
                latencyMs = 0L,
                authStatus = "Non configuré",
                databaseStatus = "Non configuré",
                sessionStatus = "Déconnecté",
                details = listOf("⚠️ Veuillez renseigner l'URL de votre serveur PocketBase.")
            )
        }

        var healthOk = false
        var healthMsg = "Inaccessible"
        var dbOk = false
        var dbMsg = "Inaccessible"

        try {
            val t0 = System.currentTimeMillis()
            val req = Request.Builder().url("$targetUrl/api/health").get().build()
            client.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - t0
                if (resp.isSuccessful) {
                    healthOk = true
                    healthMsg = "Opérationnel (${elapsed}ms)"
                    details.add("✅ Serveur PocketBase (/api/health) : Actif (${elapsed}ms)")
                } else {
                    healthMsg = "HTTP ${resp.code}"
                    details.add("⚠️ Santé serveur : HTTP ${resp.code} (${elapsed}ms)")
                }
            }
        } catch (e: Exception) {
            details.add("❌ Échec test serveur PocketBase : ${e.localizedMessage ?: e.message}")
        }

        try {
            val t1 = System.currentTimeMillis()
            val reqBuilder = Request.Builder()
                .url("$targetUrl/api/collections/v_shop_directory_light/records?perPage=1")
                .addHeader("Accept", "application/json")
                .get()
            currentSession?.takeIf { !it.isExpired }?.accessToken?.let { tok ->
                reqBuilder.addHeader("Authorization", "Bearer $tok")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - t1
                when {
                    resp.isSuccessful -> {
                        dbOk = true
                        dbMsg = "Schéma Konab prêt (${elapsed}ms)"
                        details.add("✅ Collections & vues SQLite : Opérationnelles (${elapsed}ms)")
                    }
                    resp.code == 404 -> {
                        dbMsg = "Schéma absent (404)"
                        details.add("⚠️ Importez pb_collections.json dans l'interface Admin PocketBase.")
                    }
                    else -> {
                        dbMsg = "HTTP ${resp.code}"
                        details.add("⚠️ Réponse collections : HTTP ${resp.code} (${elapsed}ms)")
                    }
                }
            }
        } catch (e: Exception) {
            details.add("❌ Échec test collections PocketBase : ${e.localizedMessage ?: e.message}")
        }

        val sessionStr = when {
            currentSession == null -> {
                details.add("ℹ️ Mode public / lecture seule actif (connectez-vous pour écrire dans les collections protégées)")
                "Mode Public / Lecture"
            }
            currentSession.isExpired -> {
                details.add("⚠️ Session PocketBase expirée (${currentSession.user.email})")
                "Session Expirée"
            }
            else -> {
                details.add("👤 Connecté : ${currentSession.user.name} (${currentSession.user.role})")
                "Session Active (${currentSession.user.email})"
            }
        }

        SupabaseAuthManager.DiagnosticResult(
            isSuccessful = healthOk && dbOk,
            url = targetUrl,
            latencyMs = System.currentTimeMillis() - startTotal,
            authStatus = healthMsg,
            databaseStatus = dbMsg,
            sessionStatus = sessionStr,
            details = details
        )
    }
}
