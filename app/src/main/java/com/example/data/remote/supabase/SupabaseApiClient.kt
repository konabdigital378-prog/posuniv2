package com.example.data.remote.supabase

import com.example.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SupabaseApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    internal val httpClient: OkHttpClient get() = client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        bodyString: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        forceAnon: Boolean = false
    ): Request {
        val url = "${SupabaseConfig.restBaseUrl}/$endpoint"
        val authToken = if (forceAnon) {
            SupabaseConfig.rawAnonKey
        } else {
            SupabaseAuthManager.currentToken ?: SupabaseConfig.rawAnonKey
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.rawAnonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Accept", "application/json")

        extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> {
                val reqBody = (bodyString ?: "[]").toRequestBody(jsonMediaType)
                requestBuilder.post(reqBody)
            }
            "PATCH" -> {
                val reqBody = (bodyString ?: "{}").toRequestBody(jsonMediaType)
                requestBuilder.patch(reqBody)
            }
            "DELETE" -> requestBuilder.delete()
        }

        return requestBuilder.build()
    }

    private inline fun <T> executeRequest(
        endpoint: String,
        method: String = "GET",
        bodyString: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        block: (okhttp3.Response) -> T
    ): T {
        val hadUserToken = SupabaseAuthManager.currentToken != null
        val primaryReq = buildRequest(endpoint, method, bodyString, extraHeaders, forceAnon = false)
        client.newCall(primaryReq).execute().use { response ->
            if (response.code == 401 && hadUserToken) {
                SupabaseAuthManager.clearExpiredSessionIfNeeded()
            } else {
                return block(response)
            }
        }
        val fallbackReq = buildRequest(endpoint, method, bodyString, extraHeaders, forceAnon = true)
        return client.newCall(fallbackReq).execute().use(block)
    }

    suspend fun testConnection(
        customUrl: String? = null,
        customKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetUrl = (customUrl?.trim()?.takeIf { it.isNotBlank() } ?: SupabaseConfig.rawUrl).trimEnd('/')
        val targetKey = (customKey?.trim()?.takeIf { it.isNotBlank() } ?: SupabaseConfig.rawAnonKey).trim()

        if (targetUrl.isBlank() || targetKey.isBlank() || !targetUrl.startsWith("http") || targetUrl.contains("your-project")) {
            return@withContext Result.failure(
                IllegalStateException("Supabase non configuré. Veuillez renseigner l'URL de votre projet (ex: https://xyz.supabase.co) et votre clé publique Anon.")
            )
        }

        try {
            val restBase = "$targetUrl/rest/v1"

            // Primary check: Query shop_profile table (Configuration Entreprise)
            val tableRequest = Request.Builder()
                .url("$restBase/shop_profile?select=id&limit=1")
                .addHeader("apikey", targetKey)
                .addHeader("Authorization", "Bearer $targetKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(tableRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connexion Supabase 100% réussie ! Synchronisation automatique de la configuration d'entreprise opérationnelle.")
                } else if (response.code == 401) {
                    val errBody = response.body?.string() ?: ""
                    Result.failure(IOException("Erreur d'authentification (HTTP 401) : La clé Anon/Publishable est invalide ou expirée. $errBody"))
                } else if (response.code == 403) {
                    val errBody = response.body?.string() ?: ""
                    Result.failure(IOException("Accès refusé (HTTP 403) : Vérifiez les politiques RLS de votre projet Supabase. $errBody"))
                } else if (response.code == 404 || response.code == 400) {
                    val errBody = response.body?.string() ?: ""
                    if (errBody.contains("relation") || errBody.contains("does not exist") ||
                        errBody.contains("PGRST205") || errBody.contains("schema cache")
                    ) {
                        Result.success(
                            "Projet Supabase connecté avec succès ! Identifiants valides. " +
                            "Les tables ne sont pas encore créées : cliquez sur 'Script SQL' ci-dessous pour copier et exécuter le script dans votre SQL Editor Supabase."
                        )
                    } else {
                        Result.failure(IOException("Erreur Supabase HTTP ${response.code} : $errBody"))
                    }
                } else {
                    val errBody = response.body?.string() ?: "Erreur HTTP ${response.code}"
                    Result.failure(IOException("Échec de connexion (${response.code}) : $errBody"))
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(IOException("Impossible de résoudre l'adresse Supabase (${e.message}). Vérifiez l'URL du projet et votre connexion Internet."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(IOException("Délai de connexion dépassé (Timeout). Le serveur Supabase ne répond pas."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- HELPERS MULTIBOUTIQUE ---

    private fun extractMissingColumnName(errorBody: String): String? {
        val pgRegex = Regex("""column\s+(?:[a-zA-Z0-9_]+\.)?([a-zA-Z0-9_]+)\s+does\s+not\s+exist""", RegexOption.IGNORE_CASE)
        pgRegex.find(errorBody)?.groupValues?.getOrNull(1)?.let { if (it.isNotBlank()) return it }
        val pgrstRegex = Regex("""Could\s+not\s+find\s+the\s+'([a-zA-Z0-9_]+)'\s+column""", RegexOption.IGNORE_CASE)
        pgrstRegex.find(errorBody)?.groupValues?.getOrNull(1)?.let { if (it.isNotBlank()) return it }
        return null
    }

    private fun stripColumnFromJsonArray(jsonArrayStr: String, column: String): String? {
        return try {
            val arr = JSONArray(jsonArrayStr)
            var removed = false
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.has(column)) {
                    obj.remove(column)
                    removed = true
                }
            }
            if (removed) arr.toString() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isMissingTableError(code: Int, body: String): Boolean {
        return code == 404 ||
            body.contains("PGRST205", ignoreCase = true) ||
            body.contains("Could not find the table", ignoreCase = true) ||
            (body.contains("relation", ignoreCase = true) && body.contains("does not exist", ignoreCase = true))
    }

    private fun mirrorNotificationId(shopId: Long, markerType: String, key: String): Long {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest("$shopId|$markerType|$key".toByteArray(Charsets.UTF_8))
            var v = 0L
            for (i in 0 until 7) {
                v = (v shl 8) or (digest[i].toLong() and 0xFFL)
            }
            (v and 0x00FFFFFFFFFFFFFFL).coerceAtLeast(100_000_000L)
        } catch (_: Exception) {
            kotlin.math.abs("$shopId|$markerType|$key".hashCode().toLong()) + 100_000_000L
        }
    }

    private fun mergeImageUriIntoGalleryJson(imageUri: String, existingGalleryJson: String): String {
        val cleanUri = imageUri.trim()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(existingGalleryJson.ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val entry = arr.optString(i, "").trim()
                if (entry.isNotBlank() && entry !in list) list.add(entry)
            }
        } catch (_: Exception) {}
        if (cleanUri.isNotBlank() && cleanUri !in list) {
            list.add(0, cleanUri)
        }
        return JSONArray(list).toString()
    }

    private fun extractFirstImageFromGalleryJson(galleryJson: String): String {
        return try {
            val arr = JSONArray(galleryJson.ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val entry = arr.optString(i, "").trim()
                if (entry.isNotBlank()) return entry
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun postJsonArrayWithColumnFallback(
        endpoint: String,
        primaryJson: String,
        fallbackJson: String? = null
    ): Pair<Boolean, Pair<Int, String>> {
        var candidateJson = primaryJson
        var triedExplicitFallback = false

        for (attempt in 0..4) {
            val (ok, code, body) = executeRequest(
                endpoint = endpoint,
                method = "POST",
                bodyString = candidateJson,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { response ->
                Triple(response.isSuccessful, response.code, response.body?.string() ?: "")
            }
            if (ok) {
                return true to (code to body)
            }
            val isColumnErr = code == 400 && (
                body.contains("column", ignoreCase = true) ||
                    body.contains("PGRST204", ignoreCase = true) ||
                    body.contains("42703", ignoreCase = true) ||
                    body.contains("schema cache", ignoreCase = true)
                )
            if (!isColumnErr) {
                return false to (code to body)
            }

            val missingCol = extractMissingColumnName(body)
            if (missingCol != null) {
                val stripped = stripColumnFromJsonArray(candidateJson, missingCol)
                if (stripped != null) {
                    candidateJson = stripped
                    continue
                }
            }
            if (fallbackJson != null && !triedExplicitFallback) {
                triedExplicitFallback = true
                candidateJson = fallbackJson
                continue
            }
            return false to (code to body)
        }
        return false to (400 to "Échec après adaptation des colonnes")
    }

    private data class RemoteShopScope(
        val canonicalShopId: Long,
        val relatedShopIds: Set<Long>,
        val includeLegacyUnscoped: Boolean
    )

    private fun resolveRemoteShopScope(
        shopId: Long?,
        fallbackShopName: String? = null
    ): RemoteShopScope {
        if ((shopId == null || shopId <= 1L) && fallbackShopName.isNullOrBlank()) {
            return RemoteShopScope(
                canonicalShopId = shopId ?: 0L,
                relatedShopIds = emptySet(),
                includeLegacyUnscoped = true
            )
        }
        val related = linkedSetOf<Long>()
        if (shopId != null && shopId > 1L) related.add(shopId)
        var totalDistinctShops = 0
        var primaryShopId: Long? = null
        var hasIdOneShop = false
        var targetNormName = fallbackShopName?.trim()?.takeIf { it.isNotBlank() }?.let {
            AgentDeploymentStorage.normalizeShopName(it)
        }
        try {
            executeRequest("shop_profile?select=id,name,updated_at&order=updated_at.desc&limit=500") { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string() ?: "[]")
                    val distinctNames = linkedSetOf<String>()
                    val allRows = mutableListOf<Pair<Long, String>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optLong("id", 0L)
                        val norm = AgentDeploymentStorage.normalizeShopName(o.optString("name", ""))
                        if (id > 0L) {
                            if (id == 1L) hasIdOneShop = true
                            if (primaryShopId == null) primaryShopId = id
                            allRows.add(id to norm)
                            distinctNames.add(if (norm.isNotBlank()) norm else "__id_$id")
                            if (shopId != null && shopId > 1L && id == shopId && targetNormName.isNullOrBlank() && norm.isNotBlank()) {
                                targetNormName = norm
                            }
                        }
                    }
                    totalDistinctShops = distinctNames.size
                    if (!targetNormName.isNullOrBlank()) {
                        allRows.forEach { (id, norm) ->
                            if (id > 1L && norm == targetNormName) {
                                related.add(id)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val canonical = related.firstOrNull() ?: (shopId ?: 0L)
        val includeLegacy = totalDistinctShops <= 1 ||
            (shopId != null && shopId == 1L) ||
            (hasIdOneShop && shopId == 1L) ||
            (primaryShopId != null && primaryShopId in related)
        return RemoteShopScope(
            canonicalShopId = canonical,
            relatedShopIds = related,
            includeLegacyUnscoped = includeLegacy
        )
    }

    private fun rowBelongsToScope(row: JSONObject, scope: RemoteShopScope): Boolean {
        if (scope.relatedShopIds.isEmpty()) return true
        val hasShopId = row.has("shop_id") && !row.isNull("shop_id")
        val rowShopId = if (hasShopId) row.optLong("shop_id", 0L) else 0L
        if (rowShopId in scope.relatedShopIds) return true
        if (!hasShopId || rowShopId == 0L || rowShopId == 1L) return scope.includeLegacyUnscoped
        return false
    }

    private fun mergeJsonRowsDeduplicated(rows: List<JSONObject>): JSONArray {
        val out = JSONArray()
        val seenKeys = hashSetOf<String>()
        rows.forEachIndexed { idx, obj ->
            val key = when {
                obj.has("id") && !obj.isNull("id") -> "id:${obj.opt("id")}"
                else -> "idx:$idx:${obj}"
            }
            if (seenKeys.add(key)) {
                out.put(obj)
            }
        }
        return out
    }

    private fun getScopedJsonRows(
        table: String,
        scope: RemoteShopScope,
        orderClause: String? = null,
        limit: Int = 2000
    ): Pair<Boolean, Pair<Int, JSONArray>> {
        val orderPart = if (!orderClause.isNullOrBlank()) "&order=$orderClause" else ""
        if (scope.relatedShopIds.isEmpty()) {
            val endpoint = "$table?select=*$orderPart&limit=$limit"
            return executeRequest(endpoint) { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    true to (resp.code to JSONArray(body.ifBlank { "[]" }))
                } else {
                    false to (resp.code to JSONArray())
                }
            }
        }

        val collected = mutableListOf<JSONObject>()
        var anySuccess = false
        var lastErrCode = 0
        var needUnscopedFallback = false

        for (targetShopId in scope.relatedShopIds) {
            val endpoint = "$table?shop_id=eq.$targetShopId&select=*$orderPart&limit=$limit"
            executeRequest(endpoint) { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    anySuccess = true
                    val arr = JSONArray(body.ifBlank { "[]" })
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { collected.add(it) }
                    }
                } else {
                    lastErrCode = resp.code
                    if (resp.code == 400 && (body.contains("shop_id", ignoreCase = true) || body.contains("column", ignoreCase = true))) {
                        needUnscopedFallback = true
                    }
                }
            }
            if (needUnscopedFallback) break
        }

        if (needUnscopedFallback) {
            val fbEndpoint = "$table?select=*$orderPart&limit=$limit"
            return executeRequest(fbEndpoint) { fbResp ->
                val fbBody = fbResp.body?.string() ?: ""
                if (fbResp.isSuccessful) {
                    true to (fbResp.code to JSONArray(fbBody.ifBlank { "[]" }))
                } else {
                    false to (fbResp.code to JSONArray())
                }
            }
        }

        if (scope.includeLegacyUnscoped) {
            val fullEndpoint = "$table?select=*$orderPart&limit=$limit"
            executeRequest(fullEndpoint) { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    anySuccess = true
                    val arr = JSONArray(body.ifBlank { "[]" })
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        if (rowBelongsToScope(obj, scope)) {
                            collected.add(obj)
                        }
                    }
                } else if (!anySuccess) {
                    lastErrCode = resp.code
                }
            }
        }

        return if (anySuccess) {
            true to (200 to mergeJsonRowsDeduplicated(collected))
        } else {
            false to (lastErrCode to JSONArray())
        }
    }

    private fun getWithShopFilterFallback(
        table: String,
        shopId: Long?,
        orderClause: String? = null,
        limit: Int = 2000,
        shopName: String? = null
    ): Pair<Boolean, Pair<Int, String>> {
        val scope = resolveRemoteShopScope(shopId, shopName)
        val (ok, codeAndArr) = getScopedJsonRows(table, scope, orderClause, limit)
        return ok to (codeAndArr.first to codeAndArr.second.toString())
    }

    // --- PRODUCTS ---

    suspend fun pushProducts(products: List<ProductEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || products.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            products.forEach { p ->
                val cloudId = MultiShopStorage.scopedCloudId(shopId, p.id)
                val mergedGallery = mergeImageUriIntoGalleryJson(p.imageUri, p.galleryImagesJson)
                val baseObj = JSONObject().apply {
                    put("id", cloudId)
                    put("name", p.name)
                    put("category", p.category)
                    put("sell_price", p.sellPrice)
                    put("buy_price", p.buyPrice)
                    put("stock_quantity", p.stockQuantity)
                    put("min_stock_alert", p.minStockAlert)
                    put("barcode", p.barcode)
                    put("unit", p.unit)
                    put("icon_name", p.iconName)
                    put("is_available", p.isAvailable)
                    if (shopId > 1L) put("shop_id", shopId)
                    put("gallery_images_json", mergedGallery)
                }
                fallbackArray.put(JSONObject(baseObj.toString()))
                baseObj.put("image_uri", p.imageUri)
                primaryArray.put(baseObj)
            }

            val (ok, codeAndBody) = postJsonArrayWithColumnFallback(
                endpoint = "products?on_conflict=id",
                primaryJson = primaryArray.toString(),
                fallbackJson = fallbackArray.toString()
            )
            if (ok) {
                Result.success(products.size)
            } else {
                Result.failure(IOException("Échec envoi produits (${codeAndBody.first}): ${codeAndBody.second}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: Long, shopId: Long = 0L): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || productId <= 0L) return@withContext Result.success(Unit)
        try {
            val cloudId = MultiShopStorage.scopedCloudId(shopId, productId)
            executeRequest(
                endpoint = "products?id=eq.$cloudId",
                method = "DELETE"
            ) { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Échec suppression produit (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProducts(shopId: Long? = null, shopName: String? = null): Result<List<ProductEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("products", shopId, orderClause = "name.asc", limit = 2000, shopName = shopName)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération produits: ${codeAndBody.first}"))
            }
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<ProductEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                val galleryJson = o.optString("gallery_images_json", "[]").ifBlank { "[]" }
                val rawImageUri = if (o.has("image_uri") && !o.isNull("image_uri")) {
                    o.optString("image_uri", "").trim()
                } else ""
                val effectiveImageUri = rawImageUri.ifBlank { extractFirstImageFromGalleryJson(galleryJson) }
                list.add(
                    ProductEntity(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", "Sans nom"),
                        category = o.optString("category", "Général"),
                        sellPrice = o.optLong("sell_price", 0L),
                        buyPrice = o.optLong("buy_price", 0L),
                        stockQuantity = o.optInt("stock_quantity", 0),
                        minStockAlert = o.optInt("min_stock_alert", 5),
                        barcode = o.optString("barcode", ""),
                        unit = o.optString("unit", "pièce"),
                        iconName = o.optString("icon_name", "default"),
                        isAvailable = o.optBoolean("is_available", true),
                        imageUri = effectiveImageUri,
                        galleryImagesJson = galleryJson
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CUSTOMERS ---

    suspend fun pushCustomers(customers: List<CustomerEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || customers.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            customers.forEach { c ->
                val cloudId = MultiShopStorage.scopedCloudId(shopId, c.id)
                val baseObj = JSONObject().apply {
                    put("id", cloudId)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("debt_amount", c.debtAmount)
                    put("total_spent", c.totalSpent)
                    put("total_sales_count", c.totalSalesCount)
                    put("status", c.status)
                    put("created_at", c.createdAt)
                }
                fallbackArray.put(JSONObject(baseObj.toString()))
                baseObj.put("shop_id", shopId)
                primaryArray.put(baseObj)
            }

            val (ok, codeAndBody) = postJsonArrayWithColumnFallback(
                endpoint = "customers?on_conflict=id",
                primaryJson = primaryArray.toString(),
                fallbackJson = fallbackArray.toString()
            )
            if (ok) Result.success(customers.size)
            else Result.failure(IOException("Échec envoi clients (${codeAndBody.first})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCustomers(shopId: Long? = null, shopName: String? = null): Result<List<CustomerEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("customers", shopId, orderClause = "total_spent.desc", limit = 2000, shopName = shopName)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération clients: ${codeAndBody.first}"))
            }
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<CustomerEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    CustomerEntity(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", "Client"),
                        phone = o.optString("phone", ""),
                        address = o.optString("address", ""),
                        debtAmount = o.optLong("debt_amount", 0L),
                        totalSpent = o.optLong("total_spent", 0L),
                        totalSalesCount = o.optInt("total_sales_count", 0),
                        status = o.optString("status", "Actif"),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SALES & SALE ITEMS ---

    suspend fun pushSales(
        sales: List<SaleEntity>,
        items: List<SaleItemEntity>,
        shopId: Long = 0L
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || sales.isEmpty()) return@withContext Result.success(0)
        try {
            val salesPrimary = JSONArray()
            val salesFallback = JSONArray()
            sales.forEach { s ->
                val cloudSaleId = MultiShopStorage.scopedCloudId(shopId, s.id)
                val baseObj = JSONObject().apply {
                    put("id", cloudSaleId)
                    put("transaction_number", s.transactionNumber)
                    put("timestamp", s.timestamp)
                    put("customer_name", s.customerName)
                    put("subtotal", s.subtotal)
                    put("discount_amount", s.discountAmount)
                    put("total_amount", s.totalAmount)
                    put("payment_method", s.paymentMethod)
                    put("payment_status", s.paymentStatus)
                    put("cashier_name", s.cashierName)
                    put("notes", s.notes)
                }
                salesFallback.put(JSONObject(baseObj.toString()))
                baseObj.put("shop_id", shopId)
                if (s.customerId != null && s.customerId > 0L) {
                    baseObj.put("customer_id", MultiShopStorage.scopedCloudId(shopId, s.customerId))
                }
                baseObj.put("total_cost_price", s.totalCostPrice)
                baseObj.put("amount_paid", s.amountPaid)
                baseObj.put("is_cancelled", s.isCancelled)
                if (s.cancelledAt != null) baseObj.put("cancelled_at", s.cancelledAt)
                if (s.cancellationReason != null) baseObj.put("cancellation_reason", s.cancellationReason)
                if (s.cancelledBy != null) baseObj.put("cancelled_by", s.cancelledBy)
                salesPrimary.put(baseObj)
            }

            val (salesOk, salesErr) = postJsonArrayWithColumnFallback(
                endpoint = "sales?on_conflict=id",
                primaryJson = salesPrimary.toString(),
                fallbackJson = salesFallback.toString()
            )
            if (!salesOk) {
                return@withContext Result.failure(IOException("Échec envoi ventes (${salesErr.first}): ${salesErr.second}"))
            }

            if (items.isNotEmpty()) {
                val itemsPrimary = JSONArray()
                val itemsFallback = JSONArray()
                items.forEach { itm ->
                    val baseObj = JSONObject().apply {
                        put("id", MultiShopStorage.scopedCloudId(shopId, itm.id))
                        put("sale_id", MultiShopStorage.scopedCloudId(shopId, itm.saleId))
                        put("product_id", if (itm.productId > 0L) MultiShopStorage.scopedCloudId(shopId, itm.productId) else itm.productId)
                        put("product_name", itm.productName)
                        put("quantity", itm.quantity)
                        put("unit_price", itm.unitPrice)
                        put("total_price", itm.totalPrice)
                    }
                    itemsFallback.put(JSONObject(baseObj.toString()))
                    baseObj.put("shop_id", shopId)
                    baseObj.put("unit_buy_price", itm.unitBuyPrice)
                    itemsPrimary.put(baseObj)
                }
                postJsonArrayWithColumnFallback(
                    endpoint = "sale_items?on_conflict=id",
                    primaryJson = itemsPrimary.toString(),
                    fallbackJson = itemsFallback.toString()
                )
            }

            Result.success(sales.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSales(shopId: Long, shopName: String? = null): Result<List<SaleEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("sales", shopId, orderClause = "timestamp.desc", limit = 2000, shopName = shopName)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération ventes: ${codeAndBody.first}"))
            }
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<SaleEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                val totalAmt = o.optLong("total_amount", 0L)
                list.add(
                    SaleEntity(
                        id = o.optLong("id", 0L),
                        transactionNumber = o.optString("transaction_number", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        customerId = if (o.has("customer_id") && !o.isNull("customer_id")) o.optLong("customer_id") else null,
                        customerName = o.optString("customer_name", "Client de passage"),
                        subtotal = o.optLong("subtotal", totalAmt),
                        discountAmount = o.optLong("discount_amount", 0L),
                        taxAmount = o.optLong("tax_amount", 0L),
                        totalAmount = totalAmt,
                        totalCostPrice = o.optLong("total_cost_price", 0L),
                        paymentMethod = o.optString("payment_method", "Espèces"),
                        paymentStatus = o.optString("payment_status", "PAYÉ"),
                        amountPaid = if (o.has("amount_paid")) o.optLong("amount_paid", totalAmt) else totalAmt,
                        cashierName = o.optString("cashier_name", "Gérant"),
                        notes = o.optString("notes", ""),
                        isCancelled = o.optBoolean("is_cancelled", false),
                        cancelledAt = if (o.has("cancelled_at") && !o.isNull("cancelled_at")) o.optLong("cancelled_at") else null,
                        cancellationReason = if (o.has("cancellation_reason") && !o.isNull("cancellation_reason")) o.optString("cancellation_reason") else null,
                        cancelledBy = if (o.has("cancelled_by") && !o.isNull("cancelled_by")) o.optString("cancelled_by") else null
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saleIdMatchesScope(
        saleIdVal: Long,
        shopId: Long,
        scope: RemoteShopScope,
        validSaleIds: Set<Long>
    ): Boolean {
        if (saleIdVal in validSaleIds) return true
        if (saleIdVal in 1L..999_999L) {
            if (MultiShopStorage.scopedCloudId(shopId, saleIdVal) in validSaleIds) return true
            if (scope.relatedShopIds.any { MultiShopStorage.scopedCloudId(it, saleIdVal) in validSaleIds }) return true
        }
        if (saleIdVal >= 1_000_000_000_000L) {
            val raw = saleIdVal % 1_000_000L
            if (raw in validSaleIds && (
                    MultiShopStorage.scopedCloudId(shopId, raw) == saleIdVal ||
                    scope.relatedShopIds.any { MultiShopStorage.scopedCloudId(it, raw) == saleIdVal }
                )
            ) {
                return true
            }
        }
        return false
    }

    suspend fun fetchSaleItems(
        shopId: Long,
        validSaleIds: Set<Long>? = null,
        shopName: String? = null
    ): Result<List<SaleItemEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val (ok, codeAndArr) = getScopedJsonRows("sale_items", scope, orderClause = "id.desc", limit = 5000)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération détails ventes: ${codeAndArr.first}"))
            }
            var jsonArr = codeAndArr.second
            if (jsonArr.length() == 0 && validSaleIds != null && validSaleIds.isNotEmpty() && !scope.includeLegacyUnscoped) {
                executeRequest("sale_items?select=*&order=id.desc&limit=5000") { fbResp ->
                    if (fbResp.isSuccessful) {
                        val rawArr = JSONArray((fbResp.body?.string() ?: "[]").ifBlank { "[]" })
                        val matchedRows = mutableListOf<JSONObject>()
                        for (i in 0 until rawArr.length()) {
                            val obj = rawArr.optJSONObject(i) ?: continue
                            val sId = obj.optLong("sale_id", 0L)
                            if (saleIdMatchesScope(sId, shopId, scope, validSaleIds)) {
                                matchedRows.add(obj)
                            }
                        }
                        if (matchedRows.isNotEmpty()) {
                            jsonArr = mergeJsonRowsDeduplicated(matchedRows)
                        }
                    }
                }
            }
            val list = mutableListOf<SaleItemEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                val saleIdVal = o.optLong("sale_id", 0L)
                if (validSaleIds != null && !saleIdMatchesScope(saleIdVal, shopId, scope, validSaleIds)) continue
                val normalizedSaleId = if (validSaleIds != null && saleIdVal !in validSaleIds) {
                    val matchedValid = validSaleIds.firstOrNull { validId ->
                        (saleIdVal in 1L..999_999L && (
                            MultiShopStorage.scopedCloudId(shopId, saleIdVal) == validId ||
                            scope.relatedShopIds.any { MultiShopStorage.scopedCloudId(it, saleIdVal) == validId }
                        )) || (validId in 1L..999_999L && (
                            MultiShopStorage.scopedCloudId(shopId, validId) == saleIdVal ||
                            scope.relatedShopIds.any { MultiShopStorage.scopedCloudId(it, validId) == saleIdVal }
                        ))
                    }
                    matchedValid ?: saleIdVal
                } else {
                    saleIdVal
                }
                list.add(
                    SaleItemEntity(
                        id = o.optLong("id", 0L),
                        saleId = normalizedSaleId,
                        productId = o.optLong("product_id", 0L),
                        productName = o.optString("product_name", "Article"),
                        quantity = o.optInt("quantity", 1),
                        unitPrice = o.optLong("unit_price", 0L),
                        unitBuyPrice = o.optLong("unit_buy_price", 0L),
                        totalPrice = o.optLong("total_price", 0L)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SHOP PROFILE & UNIQUENESS ---

    private data class RemoteShopMatch(
        val id: Long,
        val name: String,
        val deployerId: String?
    )

    private suspend fun findRemoteShopByName(shopName: String): Result<RemoteShopMatch?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(null)
        val normalizedTarget = AgentDeploymentStorage.normalizeShopName(shopName)
        if (normalizedTarget.isBlank()) return@withContext Result.success(null)

        try {
            executeRequest("shop_profile?select=id,name,deployer_id&limit=1000") { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string() ?: "[]")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val rowName = AgentDeploymentStorage.normalizeShopName(o.optString("name", ""))
                        if (rowName == normalizedTarget) {
                            val depId = if (o.has("deployer_id") && !o.isNull("deployer_id")) {
                                o.optString("deployer_id", "").trim().ifBlank { null }
                            } else null
                            return@withContext Result.success(
                                RemoteShopMatch(
                                    id = o.optLong("id", 0L),
                                    name = o.optString("name", ""),
                                    deployerId = depId
                                )
                            )
                        }
                    }
                    return@withContext Result.success(null)
                }

                // Fallback si une ancienne table n'a pas encore la colonne deployer_id
                if (resp.code == 400) {
                    executeRequest("shop_profile?select=id,name&limit=1000") { fallbackResp ->
                        if (fallbackResp.isSuccessful) {
                            val arr = JSONArray(fallbackResp.body?.string() ?: "[]")
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val rowName = AgentDeploymentStorage.normalizeShopName(o.optString("name", ""))
                                if (rowName == normalizedTarget) {
                                    return@withContext Result.success(
                                        RemoteShopMatch(
                                            id = o.optLong("id", 0L),
                                            name = o.optString("name", ""),
                                            deployerId = null
                                        )
                                    )
                                }
                            }
                            return@withContext Result.success(null)
                        }
                    }
                }

                val err = resp.body?.string() ?: ""
                Result.failure(IOException("Erreur vérification unicité boutique (${resp.code}): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun findRemoteDeploymentIdByShopName(shopName: String): Result<String?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(null)
        val normalizedTarget = AgentDeploymentStorage.normalizeShopName(shopName)
        if (normalizedTarget.isBlank()) return@withContext Result.success(null)

        try {
            executeRequest("agent_deployments?select=id,shop_name&limit=1000") { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Erreur lecture déploiements (${resp.code}): $err"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val deployedName = AgentDeploymentStorage.normalizeShopName(o.optString("shop_name", ""))
                    if (deployedName == normalizedTarget) {
                        val depId = o.optString("id", "").trim()
                        if (depId.isNotBlank()) return@withContext Result.success(depId)
                    }
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkShopNameExists(
        shopName: String,
        excludeShopId: Long? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(false)
        val normalizedTarget = AgentDeploymentStorage.normalizeShopName(shopName)
        if (normalizedTarget.isBlank()) return@withContext Result.success(false)

        val shopMatchRes = findRemoteShopByName(shopName)
        if (shopMatchRes.isFailure) {
            return@withContext Result.failure(shopMatchRes.exceptionOrNull() ?: IOException("Erreur vérification unicité boutique"))
        }
        val shopMatch = shopMatchRes.getOrNull()
        if (shopMatch != null && (excludeShopId == null || shopMatch.id != excludeShopId)) {
            return@withContext Result.success(true)
        }

        // Check agent_deployments when creating a new shop
        if (excludeShopId == null) {
            val depMatchRes = findRemoteDeploymentIdByShopName(shopName)
            if (depMatchRes.isSuccess && depMatchRes.getOrNull() != null) {
                return@withContext Result.success(true)
            }
        }

        Result.success(false)
    }

    suspend fun fetchShopsForAgent(agentId: String): Result<List<ShopProfileEntity>> = withContext(Dispatchers.IO) {
        val cleanId = agentId.trim().uppercase()
        if (!SupabaseConfig.isConfigured || cleanId.isBlank()) return@withContext Result.success(emptyList())
        try {
            val encoded = URLEncoder.encode(cleanId, "UTF-8")
            executeRequest("shop_profile?deployer_id=eq.$encoded&order=updated_at.desc") { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec chargement boutiques agent (${response.code}): $err"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<ShopProfileEntity>()
                for (i in 0 until jsonArr.length()) {
                    val o = jsonArr.getJSONObject(i)
                    list.add(parseShopProfile(o))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseShopProfile(o: JSONObject): ShopProfileEntity {
        val rawLogo = if (o.has("logo_uri") && !o.isNull("logo_uri")) {
            o.optString("logo_uri", "").trim().takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true)
            }
        } else null
        val remoteAdminPin = if (o.has("admin_pin") && !o.isNull("admin_pin")) {
            o.optString("admin_pin", "").trim().ifBlank { "1234" }
        } else "1234"
        val remoteCashierPin = if (o.has("cashier_pin") && !o.isNull("cashier_pin")) {
            o.optString("cashier_pin", "").trim().ifBlank { "0000" }
        } else "0000"
        return ShopProfileEntity(
            id = o.optInt("id", 1),
            name = o.optString("name", "Konab POS"),
            businessType = o.optString("business_type", "Boutique"),
            phone = o.optString("phone", ""),
            address = o.optString("address", ""),
            city = o.optString("city", ""),
            currency = o.optString("currency", "FCFA"),
            slogan = o.optString("slogan", ""),
            receiptFooter = o.optString("receipt_footer", ""),
            adminPin = remoteAdminPin,
            cashierPin = remoteCashierPin,
            subscriptionTier = o.optString("subscription_tier", "Premium"),
            logoUri = rawLogo,
            deployerId = if (o.has("deployer_id") && !o.isNull("deployer_id")) o.getString("deployer_id") else null,
            isConfigured = true
        )
    }

    private fun parseConnectedShopSession(o: JSONObject): ConnectedShopSession {
        val shopId = o.optLong("id", 0L)
        val profile = parseShopProfile(o)
        val adminName = if (o.has("admin_name") && !o.isNull("admin_name")) {
            o.optString("admin_name", "").trim().ifBlank { "Gérant" }
        } else "Gérant"
        val adminPhone = if (o.has("admin_phone") && !o.isNull("admin_phone")) {
            o.optString("admin_phone", "").trim().ifBlank { profile.phone }
        } else profile.phone
        return ConnectedShopSession(
            cloudShopId = shopId,
            shopName = profile.name,
            businessType = profile.businessType,
            city = profile.city,
            address = profile.address,
            phone = profile.phone,
            slogan = profile.slogan,
            currency = profile.currency,
            receiptFooter = profile.receiptFooter,
            logoUri = profile.logoUri,
            adminName = adminName,
            adminPhone = adminPhone,
            adminPin = profile.adminPin,
            cashierPin = profile.cashierPin,
            subscriptionTier = profile.subscriptionTier,
            deployerId = profile.deployerId,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    suspend fun authenticateShopManagerRemote(
        identifier: String,
        pin: String
    ): Result<RemoteShopLoginMatch?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase non configuré."))
        }
        val cleanId = identifier.trim()
        val cleanPin = pin.trim()
        if (cleanId.isBlank() || cleanPin.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Veuillez renseigner l'identifiant de la boutique et le code PIN du gérant."))
        }
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=1000") { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Erreur connexion Supabase (${response.code}): $err"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                var firstIdMatchName: String? = null
                for (i in 0 until jsonArr.length()) {
                    val o = jsonArr.getJSONObject(i)
                    val cloudId = o.optLong("id", 0L)
                    val session = parseConnectedShopSession(o)
                    val matchesId = MultiShopStorage.identifierMatchesSession(
                        identifier = cleanId,
                        shopName = session.shopName,
                        shopPhone = session.phone,
                        adminName = session.adminName,
                        adminPhone = session.adminPhone,
                        cloudShopId = cloudId
                    )
                    if (matchesId) {
                        if (firstIdMatchName == null) {
                            firstIdMatchName = session.shopName
                        }
                        val storedPin = if (o.has("admin_pin") && !o.isNull("admin_pin")) {
                            o.optString("admin_pin", "").trim()
                        } else ""
                        val expectedPin = storedPin.ifBlank { "1234" }
                        if (expectedPin == cleanPin) {
                            return@withContext Result.success(
                                RemoteShopLoginMatch(
                                    cloudShopId = cloudId,
                                    session = session,
                                    profile = session.toShopProfileEntity()
                                )
                            )
                        }
                    }
                }
                if (firstIdMatchName != null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Code PIN gérant incorrect pour la boutique « $firstIdMatchName ».")
                    )
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllRegisteredShops(): Result<List<ShopProfileEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=200") { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec chargement boutiques (${response.code}): $err"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<ShopProfileEntity>()
                for (i in 0 until jsonArr.length()) {
                    val profile = parseShopProfile(jsonArr.getJSONObject(i))
                    if (profile.name.isNotBlank()) {
                        list.add(profile)
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushShopProfile(
        profile: ShopProfileEntity,
        cloudShopId: Long = 0L,
        isNewShop: Boolean = false,
        adminName: String = "Gérant",
        adminPhone: String = ""
    ): Result<Long> = withContext(Dispatchers.IO) {
        val requestedId = if (cloudShopId > 1L) cloudShopId else if (profile.id > 1L) profile.id.toLong() else System.currentTimeMillis()
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(requestedId)
        val cleanName = profile.name.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Le nom de la boutique est obligatoire."))
        }
        val cleanDeployerId = profile.deployerId?.trim()?.uppercase().orEmpty()
        if (isNewShop && cleanDeployerId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Un compte Agent Déployeur est obligatoire pour créer une nouvelle boutique.")
            )
        }

        try {
            val remoteLookup = findRemoteShopByName(cleanName)
            if (remoteLookup.isFailure) {
                return@withContext Result.failure(
                    remoteLookup.exceptionOrNull() ?: IOException("Impossible de vérifier la boutique sur Supabase.")
                )
            }
            val existingRemote = remoteLookup.getOrNull()

            val targetId = if (isNewShop) {
                if (existingRemote != null) {
                    if (cloudShopId > 1L && existingRemote.id == cloudShopId) {
                        existingRemote.id
                    } else {
                        return@withContext Result.failure(
                            IllegalStateException("Une boutique portant le nom « $cleanName » existe déjà. Chaque boutique doit avoir un nom unique.")
                        )
                    }
                } else {
                    val depLookup = findRemoteDeploymentIdByShopName(cleanName)
                    if (depLookup.isSuccess && depLookup.getOrNull() != null) {
                        return@withContext Result.failure(
                            IllegalStateException("Une boutique portant le nom « $cleanName » existe déjà. Chaque boutique doit avoir un nom unique.")
                        )
                    }
                    requestedId
                }
            } else {
                if (existingRemote != null) {
                    val sameShopOrOwner = existingRemote.id == requestedId ||
                        existingRemote.deployerId.isNullOrBlank() ||
                        existingRemote.deployerId.equals(cleanDeployerId, ignoreCase = true)
                    if (sameShopOrOwner) {
                        existingRemote.id
                    } else {
                        return@withContext Result.failure(
                            IllegalStateException("Une boutique portant le nom « $cleanName » existe déjà. Chaque boutique doit avoir un nom unique.")
                        )
                    }
                } else {
                    requestedId
                }
            }

            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

            val effectiveAdminName = adminName.trim().ifBlank { "Gérant" }
            val effectiveAdminPhone = adminPhone.trim().ifBlank { profile.phone.trim() }

            fun buildStoreObj(includeLogo: Boolean, includeManagerCreds: Boolean): JSONObject = JSONObject().apply {
                put("id", targetId)
                put("name", cleanName)
                put("business_type", profile.businessType)
                put("phone", profile.phone)
                put("address", profile.address)
                put("city", profile.city)
                put("currency", profile.currency)
                put("slogan", profile.slogan)
                put("receipt_footer", profile.receiptFooter)
                put("subscription_tier", profile.subscriptionTier)
                put("deployer_id", cleanDeployerId)
                if (includeLogo) {
                    put("logo_uri", profile.logoUri?.trim().orEmpty())
                }
                if (includeManagerCreds) {
                    put("admin_name", effectiveAdminName)
                    put("admin_phone", effectiveAdminPhone)
                    put("admin_pin", profile.adminPin.trim().ifBlank { "1234" })
                    put("cashier_pin", profile.cashierPin.trim().ifBlank { "0000" })
                }
                put("updated_at", nowIso)
            }

            fun executePush(includeLogo: Boolean, includeManagerCreds: Boolean): Pair<Boolean, Pair<Int, String>> {
                val array = JSONArray().apply { put(buildStoreObj(includeLogo, includeManagerCreds)) }
                return executeRequest(
                    endpoint = "shop_profile?on_conflict=id",
                    method = "POST",
                    bodyString = array.toString(),
                    extraHeaders = mapOf(
                        "Prefer" to "resolution=merge-duplicates"
                    )
                ) { response ->
                    val body = response.body?.string() ?: ""
                    response.isSuccessful to (response.code to body)
                }
            }

            var includeLogo = true
            var includeCreds = true
            var (ok, codeAndBody) = executePush(includeLogo = includeLogo, includeManagerCreds = includeCreds)
            if (!ok && codeAndBody.first == 400) {
                if (codeAndBody.second.contains("admin_pin", ignoreCase = true) ||
                    codeAndBody.second.contains("admin_phone", ignoreCase = true) ||
                    codeAndBody.second.contains("admin_name", ignoreCase = true) ||
                    codeAndBody.second.contains("cashier_pin", ignoreCase = true)
                ) {
                    includeCreds = false
                }
                if (codeAndBody.second.contains("logo_uri", ignoreCase = true)) {
                    includeLogo = false
                }
                if (!includeCreds || !includeLogo) {
                    val retry = executePush(includeLogo = includeLogo, includeManagerCreds = includeCreds)
                    ok = retry.first
                    codeAndBody = retry.second
                }
                if (!ok && codeAndBody.first == 400 && includeLogo && codeAndBody.second.contains("logo_uri", ignoreCase = true)) {
                    val retry2 = executePush(includeLogo = false, includeManagerCreds = includeCreds)
                    ok = retry2.first
                    codeAndBody = retry2.second
                }
            }

            if (ok) {
                android.util.Log.i("KonabSupabase", "Boutique \"$cleanName\" (ID $targetId, Agent $cleanDeployerId) synchronisée sur Supabase")
                Result.success(targetId)
            } else {
                val code = codeAndBody.first
                val errBody = codeAndBody.second
                android.util.Log.e("KonabSupabase", "Échec push boutique ($code): $errBody")
                if (code == 409 || errBody.contains("unique", ignoreCase = true) || errBody.contains("23505")) {
                    Result.failure(
                        IllegalStateException("Une boutique portant le nom « $cleanName » existe déjà dans la base de données.")
                    )
                } else {
                    Result.failure(IOException("Échec mise à jour boutique: $code $errBody"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KonabSupabase", "Exception pushShopProfile", e)
            Result.failure(e)
        }
    }

    suspend fun fetchShopProfile(): Result<ShopProfileEntity?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(null)
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=1") { response ->
                if (!response.isSuccessful) return@withContext Result.success(null)
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                if (jsonArr.length() == 0) return@withContext Result.success(null)
                Result.success(parseShopProfile(jsonArr.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchShopProfileById(shopId: Long): Result<ShopProfileEntity?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(null)
        try {
            executeRequest("shop_profile?id=eq.$shopId&select=*&limit=1") { response ->
                if (!response.isSuccessful) return@withContext Result.success(null)
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                if (jsonArr.length() == 0) return@withContext Result.success(null)
                Result.success(parseShopProfile(jsonArr.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SUPPLIERS ---

    suspend fun pushSuppliers(suppliers: List<SupplierEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || suppliers.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            suppliers.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", MultiShopStorage.scopedCloudId(shopId, s.id))
                    put("name", s.name)
                    put("phone", s.phone)
                    put("address", s.address)
                    put("debt_amount", s.debtAmount)
                    put("total_purchased", s.totalPurchased)
                    put("created_at", s.createdAt)
                }
                fallbackArray.put(JSONObject(obj.toString()))
                obj.put("shop_id", shopId)
                primaryArray.put(obj)
            }
            val (ok, err) = postJsonArrayWithColumnFallback("suppliers?on_conflict=id", primaryArray.toString(), fallbackArray.toString())
            if (ok) Result.success(suppliers.size)
            else Result.failure(IOException("Échec envoi fournisseurs: ${err.first}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSuppliers(shopId: Long, shopName: String? = null): Result<List<SupplierEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("suppliers", shopId, orderClause = "name.asc", limit = 2000, shopName = shopName)
            if (!ok) return@withContext Result.failure(IOException("Échec récupération fournisseurs: ${codeAndBody.first}"))
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<SupplierEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    SupplierEntity(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", "Fournisseur"),
                        phone = o.optString("phone", ""),
                        address = o.optString("address", ""),
                        debtAmount = o.optLong("debt_amount", 0L),
                        totalPurchased = o.optLong("total_purchased", 0L),
                        createdAt = o.optLong("created_at", 0L)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PURCHASES ---

    suspend fun pushPurchases(purchases: List<PurchaseEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || purchases.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            purchases.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", MultiShopStorage.scopedCloudId(shopId, p.id))
                    put("invoice_number", p.invoiceNumber)
                    put("timestamp", p.timestamp)
                    put("supplier_id", MultiShopStorage.scopedCloudId(shopId, p.supplierId))
                    put("supplier_name", p.supplierName)
                    put("product_id", MultiShopStorage.scopedCloudId(shopId, p.productId))
                    put("product_name", p.productName)
                    put("quantity", p.quantity)
                    put("unit_cost", p.unitCost)
                    put("total_cost", p.totalCost)
                    put("is_paid_from_cash", p.isPaidFromCash)
                    put("notes", p.notes)
                }
                fallbackArray.put(JSONObject(obj.toString()))
                obj.put("shop_id", shopId)
                primaryArray.put(obj)
            }
            val (ok, err) = postJsonArrayWithColumnFallback("purchases?on_conflict=id", primaryArray.toString(), fallbackArray.toString())
            if (ok) Result.success(purchases.size)
            else Result.failure(IOException("Échec envoi achats: ${err.first}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPurchases(shopId: Long, shopName: String? = null): Result<List<PurchaseEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("purchases", shopId, orderClause = "timestamp.desc", limit = 2000, shopName = shopName)
            if (!ok) return@withContext Result.failure(IOException("Échec récupération achats: ${codeAndBody.first}"))
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<PurchaseEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    PurchaseEntity(
                        id = o.optLong("id", 0L),
                        invoiceNumber = o.optString("invoice_number", ""),
                        timestamp = o.optLong("timestamp", 0L),
                        supplierId = o.optLong("supplier_id", 0L),
                        supplierName = o.optString("supplier_name", ""),
                        productId = o.optLong("product_id", 0L),
                        productName = o.optString("product_name", ""),
                        quantity = o.optInt("quantity", 0),
                        unitCost = o.optLong("unit_cost", 0L),
                        totalCost = o.optLong("total_cost", 0L),
                        isPaidFromCash = o.optBoolean("is_paid_from_cash", true),
                        notes = o.optString("notes", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CASH MOVEMENTS ---

    suspend fun pushCashMovements(movements: List<CashMovementEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || movements.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            movements.forEach { m ->
                val obj = JSONObject().apply {
                    put("id", MultiShopStorage.scopedCloudId(shopId, m.id))
                    put("timestamp", m.timestamp)
                    put("type", m.type)
                    put("amount", m.amount)
                    put("reason", m.reason)
                    put("payment_method", m.paymentMethod)
                    put("operator_name", m.operatorName)
                    if (m.relatedId != null) put("related_id", MultiShopStorage.scopedCloudId(shopId, m.relatedId))
                }
                fallbackArray.put(JSONObject(obj.toString()))
                obj.put("shop_id", shopId)
                primaryArray.put(obj)
            }
            val (ok, err) = postJsonArrayWithColumnFallback("cash_movements?on_conflict=id", primaryArray.toString(), fallbackArray.toString())
            if (ok) Result.success(movements.size)
            else Result.failure(IOException("Échec envoi mouvements caisse: ${err.first}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCashMovements(shopId: Long, shopName: String? = null): Result<List<CashMovementEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("cash_movements", shopId, orderClause = "timestamp.desc", limit = 2000, shopName = shopName)
            if (!ok) return@withContext Result.failure(IOException("Échec récupération caisse: ${codeAndBody.first}"))
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<CashMovementEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    CashMovementEntity(
                        id = o.optLong("id", 0L),
                        timestamp = o.optLong("timestamp", 0L),
                        type = o.optString("type", "CASH_IN"),
                        amount = o.optLong("amount", 0L),
                        reason = o.optString("reason", ""),
                        paymentMethod = o.optString("payment_method", "Espèces"),
                        operatorName = o.optString("operator_name", "Gérant"),
                        relatedId = if (o.has("related_id") && !o.isNull("related_id")) o.optLong("related_id") else null
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CASH SESSIONS ---

    suspend fun pushCashSessions(sessions: List<CashSessionEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || sessions.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            sessions.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", MultiShopStorage.scopedCloudId(shopId, s.id))
                    put("opened_at", s.openedAt)
                    if (s.closedAt != null) put("closed_at", s.closedAt)
                    put("opening_balance", s.openingBalance)
                    put("closing_expected_balance", s.closingExpectedBalance)
                    put("closing_actual_balance", s.closingActualBalance)
                    put("difference", s.difference)
                    put("status", s.status)
                    put("opened_by", s.openedBy)
                    if (s.closedBy != null) put("closed_by", s.closedBy)
                    put("closing_notes", s.closingNotes)
                }
                fallbackArray.put(JSONObject(obj.toString()))
                obj.put("shop_id", shopId)
                primaryArray.put(obj)
            }
            val (ok, err) = postJsonArrayWithColumnFallback("cash_sessions?on_conflict=id", primaryArray.toString(), fallbackArray.toString())
            if (ok) Result.success(sessions.size)
            else Result.failure(IOException("Échec envoi sessions: ${err.first}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCashSessions(shopId: Long, shopName: String? = null): Result<List<CashSessionEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("cash_sessions", shopId, orderClause = "opened_at.desc", limit = 500, shopName = shopName)
            if (!ok) return@withContext Result.failure(IOException("Échec récupération sessions caisse: ${codeAndBody.first}"))
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<CashSessionEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                list.add(
                    CashSessionEntity(
                        id = o.optLong("id", 0L),
                        openedAt = o.optLong("opened_at", 0L),
                        closedAt = if (o.has("closed_at") && !o.isNull("closed_at")) o.optLong("closed_at") else null,
                        openingBalance = o.optLong("opening_balance", 0L),
                        closingExpectedBalance = o.optLong("closing_expected_balance", 0L),
                        closingActualBalance = o.optLong("closing_actual_balance", 0L),
                        difference = o.optLong("difference", 0L),
                        status = o.optString("status", "OPEN"),
                        openedBy = o.optString("opened_by", "Gérant"),
                        closedBy = if (o.has("closed_by") && !o.isNull("closed_by")) o.optString("closed_by") else null,
                        closingNotes = o.optString("closing_notes", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---

    suspend fun pushNotifications(notifications: List<NotificationEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || notifications.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            notifications.forEach { n ->
                val obj = JSONObject().apply {
                    put("id", MultiShopStorage.scopedCloudId(shopId, n.id))
                    put("title", n.title)
                    put("message", n.message)
                    put("type", n.type)
                    put("priority", n.priority)
                    put("timestamp", n.timestamp)
                    put("is_read", n.isRead)
                    if (n.actionRoute != null) put("action_route", n.actionRoute)
                    if (n.referenceId != null) put("reference_id", MultiShopStorage.scopedCloudId(shopId, n.referenceId))
                }
                fallbackArray.put(JSONObject(obj.toString()))
                obj.put("shop_id", shopId)
                primaryArray.put(obj)
            }
            val (ok, err) = postJsonArrayWithColumnFallback("app_notifications?on_conflict=id", primaryArray.toString(), fallbackArray.toString())
            if (ok) Result.success(notifications.size)
            else Result.failure(IOException("Échec envoi notifications: ${err.first}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- AGENTS & DEPLOYMENTS ---

    suspend fun pushAgentDeployments(deployments: List<AgentDeploymentRecord>): Result<Int> = withContext(Dispatchers.IO) {
        val dedupedValid = deployments
            .filter { it.agentId.isNotBlank() && it.shopName.isNotBlank() }
            .sortedByDescending { it.timestamp }
            .distinctBy { AgentDeploymentStorage.normalizeShopName(it.shopName) }
        if (!SupabaseConfig.isConfigured || dedupedValid.isEmpty()) return@withContext Result.success(0)
        try {
            // Réutiliser l'ID distant existant si la boutique a déjà une entrée dans agent_deployments
            val existingRemoteIdsByName = mutableMapOf<String, String>()
            try {
                executeRequest("agent_deployments?select=id,shop_name&limit=1000") { existingResp ->
                    if (existingResp.isSuccessful) {
                        val existingArr = JSONArray(existingResp.body?.string() ?: "[]")
                        for (i in 0 until existingArr.length()) {
                            val o = existingArr.getJSONObject(i)
                            val rId = o.optString("id", "").trim()
                            val rName = AgentDeploymentStorage.normalizeShopName(o.optString("shop_name", ""))
                            if (rId.isNotBlank() && rName.isNotBlank() && !existingRemoteIdsByName.containsKey(rName)) {
                                existingRemoteIdsByName[rName] = rId
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val array = JSONArray()
            dedupedValid.forEach { d ->
                val normShopName = AgentDeploymentStorage.normalizeShopName(d.shopName)
                val effectiveId = existingRemoteIdsByName[normShopName] ?: d.id
                val obj = JSONObject().apply {
                    put("id", effectiveId)
                    put("agent_id", d.agentId.trim().uppercase())
                    put("agent_name", d.agentName)
                    put("agent_phone", d.agentPhone)
                    put("shop_name", d.shopName.trim())
                    put("shop_phone", d.shopPhone)
                    put("shop_city", d.shopCity)
                    put("tier", d.tier)
                    put("duration_days", d.durationDays)
                    put("license_key", d.licenseKey)
                    put("notes", d.notes)
                    put("timestamp", d.timestamp)
                }
                array.put(obj)
            }
            executeRequest(
                endpoint = "agent_deployments?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) {
                    Result.success(dedupedValid.size)
                } else {
                    val err = resp.body?.string() ?: ""
                    Result.failure(IOException("Échec synchronisation déploiements (${resp.code}): $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAgentDeployments(agentId: String? = null): Result<List<AgentDeploymentRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val endpoint = if (!agentId.isNullOrBlank()) {
                val encodedId = URLEncoder.encode(agentId.trim().uppercase(), "UTF-8")
                "agent_deployments?agent_id=eq.$encodedId&order=timestamp.desc"
            } else {
                "agent_deployments?select=*&order=timestamp.desc"
            }
            executeRequest(endpoint) { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec chargement déploiements (${resp.code}): $err"))
                }
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<AgentDeploymentRecord>()
                for (i in 0 until jsonArr.length()) {
                    list.add(AgentDeploymentRecord.fromJson(jsonArr.getJSONObject(i)).copy(isSynced = true))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCloudAgents(): Result<List<com.example.ui.screens.AgentUser>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("agents?select=*&order=id.asc") { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec chargement agents (${resp.code}): $err"))
                }
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<com.example.ui.screens.AgentUser>()
                for (i in 0 until jsonArr.length()) {
                    val o = jsonArr.getJSONObject(i)
                    list.add(
                        com.example.ui.screens.AgentUser(
                            id = o.optString("id", ""),
                            name = o.optString("name", "Agent"),
                            phone = o.optString("phone", ""),
                            zone = o.optString("zone", "National"),
                            pin = o.optString("pin", "1234"),
                            role = o.optString("role", "Agent Déployeur")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushCloudAgent(agent: com.example.ui.screens.AgentUser): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.failure(IllegalStateException("Supabase non configuré"))
        try {
            val obj = JSONObject().apply {
                put("id", agent.id)
                put("name", agent.name)
                put("phone", agent.phone)
                put("zone", agent.zone)
                put("pin", agent.pin)
                put("role", agent.role)
                put("is_active", true)
            }
            val array = JSONArray().apply { put(obj) }
            executeRequest(
                endpoint = "agents",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else {
                    val err = resp.body?.string() ?: ""
                    Result.failure(IOException("Échec enregistrement agent (${resp.code}): $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerCloudAgent(agent: com.example.ui.screens.AgentUser): Result<com.example.ui.screens.AgentUser> = withContext(Dispatchers.IO) {
        val pushRes = pushCloudAgent(agent)
        if (pushRes.isSuccess) {
            Result.success(agent)
        } else {
            Result.failure(pushRes.exceptionOrNull() ?: IOException("Erreur enregistrement agent"))
        }
    }

    suspend fun authenticateCloudAgent(idOrPhone: String, pin: String): Result<com.example.ui.screens.AgentUser?> = withContext(Dispatchers.IO) {
        val cleanInput = idOrPhone.trim()
        val cleanPin = pin.trim()
        val agentsRes = fetchCloudAgents()
        if (agentsRes.isSuccess) {
            val agents = agentsRes.getOrDefault(emptyList())
            val matched = agents.find { agt ->
                (agt.id.equals(cleanInput, ignoreCase = true) ||
                 agt.phone.replace(" ", "") == cleanInput.replace(" ", "")) &&
                agt.pin == cleanPin
            }
            Result.success(matched)
        } else {
            Result.failure(agentsRes.exceptionOrNull() ?: IOException("Impossible de contacter Supabase"))
        }
    }

    // --- SHOP ADS & VIDEO STORAGE ---

    suspend fun fetchShopAds(): Result<List<ShopAdRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("shop_ads?select=*&order=updated_at.desc&limit=200") { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec chargement publicités (${resp.code}): $err"))
                }
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<ShopAdRecord>()
                for (i in 0 until jsonArr.length()) {
                    val ad = ShopAdRecord.fromJson(jsonArr.getJSONObject(i))
                        .copy(isSynced = true, isFallbackShopOnly = false)
                    if (ad.shopName.isNotBlank()) {
                        list.add(ad)
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushShopAd(ad: ShopAdRecord): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.failure(IllegalStateException("Supabase non configuré"))
        try {
            val obj = JSONObject().apply {
                put("id", ad.id)
                put("shop_id", ad.shopId)
                put("shop_name", ad.shopName.trim())
                put("business_type", ad.businessType.ifBlank { "Boutique" })
                put("city", ad.city.trim())
                put("phone", ad.phone.trim())
                put("whatsapp_phone", ad.whatsappPhone.trim().ifBlank { ad.phone.trim() })
                put("logo_uri", ad.logoUri.trim())
                put("ad_title", ad.adTitle.trim().ifBlank { ad.shopName.trim() })
                put("ad_description", ad.adDescription.trim())
                put("video_uri", ad.videoUri.trim())
                put("video_duration_sec", ShopAdStorage.clampVideoDurationSec(ad.videoDurationSec, ad.hasVideo))
                put("cta_label", ad.ctaLabel.trim().ifBlank { "Contacter" })
                put("is_active", ad.isActive)
                put("deployer_id", ad.deployerId.trim())
                put("updated_at", ad.updatedAt)
            }
            val array = JSONArray().apply { put(obj) }
            executeRequest(
                endpoint = "shop_ads?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val err = resp.body?.string() ?: ""
                    Result.failure(IOException("Échec publication publicité (${resp.code}): $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteShopAd(adId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || adId.isBlank()) return@withContext Result.success(Unit)
        try {
            val encodedId = URLEncoder.encode(adId, "UTF-8")
            executeRequest(
                endpoint = "shop_ads?id=eq.$encodedId",
                method = "DELETE"
            ) { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec suppression publicité (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadToStorageBucket(
        bucket: String,
        file: File,
        remotePath: String,
        contentType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase non configuré"))
        }
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Fichier média introuvable"))
        }
        try {
            val cleanBase = SupabaseConfig.rawUrl.trimEnd('/')
            val cleanBucket = bucket.trim().trim('/')
            val cleanPath = remotePath.trimStart('/')
            val uploadUrl = "$cleanBase/storage/v1/object/$cleanBucket/$cleanPath"
            val publicUrl = "$cleanBase/storage/v1/object/public/$cleanBucket/$cleanPath"
            val mediaType = contentType.toMediaType()
            fun buildUploadReq(forceAnon: Boolean, usePut: Boolean = false): Request {
                val authToken = if (forceAnon) {
                    SupabaseConfig.rawAnonKey
                } else {
                    SupabaseAuthManager.currentToken ?: SupabaseConfig.rawAnonKey
                }
                val builder = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", SupabaseConfig.rawAnonKey)
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("x-upsert", "true")
                return if (usePut) {
                    builder.put(file.asRequestBody(mediaType)).build()
                } else {
                    builder.post(file.asRequestBody(mediaType)).build()
                }
            }

            val hadUserToken = SupabaseAuthManager.currentToken != null
            var shouldRetryAnon = false
            var shouldRetryPut = false
            client.newCall(buildUploadReq(forceAnon = false, usePut = false)).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext Result.success(publicUrl)
                } else if (resp.code == 401 && hadUserToken) {
                    SupabaseAuthManager.clearExpiredSessionIfNeeded()
                    shouldRetryAnon = true
                } else if (resp.code == 409) {
                    shouldRetryPut = true
                } else {
                    val err = resp.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Échec upload média (${resp.code}): $err"))
                }
            }
            if (shouldRetryAnon) {
                client.newCall(buildUploadReq(forceAnon = true, usePut = false)).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success(publicUrl)
                    } else if (resp.code == 409) {
                        shouldRetryPut = true
                    } else {
                        val err = resp.body?.string() ?: ""
                        return@withContext Result.failure(IOException("Échec upload média (${resp.code}): $err"))
                    }
                }
            }
            if (shouldRetryPut) {
                client.newCall(buildUploadReq(forceAnon = shouldRetryAnon, usePut = true)).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success(publicUrl)
                    } else {
                        val err = resp.body?.string() ?: ""
                        return@withContext Result.failure(IOException("Échec mise à jour média (${resp.code}): $err"))
                    }
                }
            }
            Result.failure(IOException("Échec upload média"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAdMedia(
        file: File,
        remotePath: String,
        contentType: String
    ): Result<String> = uploadToStorageBucket(
        bucket = "shop-ads",
        file = file,
        remotePath = remotePath,
        contentType = contentType
    )

    /**
     * Uploads a compressed shop logo JPEG into the dedicated public `shop-logos` Storage bucket,
     * falling back to `shop-ads` if `shop-logos` has not been created on the remote project yet.
     */
    suspend fun uploadShopLogoMedia(
        file: File,
        shopId: Long,
        contentType: String = "image/jpeg"
    ): Result<String> {
        val safeShopId = shopId.coerceAtLeast(1L)
        val fileName = "shop_${safeShopId}_${System.currentTimeMillis()}.jpg"
        val primary = uploadToStorageBucket(
            bucket = "shop-logos",
            file = file,
            remotePath = fileName,
            contentType = contentType
        )
        if (primary.isSuccess) return primary
        return uploadToStorageBucket(
            bucket = "shop-ads",
            file = file,
            remotePath = "shop-logos/$fileName",
            contentType = contentType
        )
    }

    // --- RESTAURANT MULTI-USER SYNC, STAFF, TABLES, MENU DU JOUR & ORDERS ---

    data class PublicRestaurantMenuBundle(
        val shopId: Long,
        val shopName: String,
        val city: String,
        val phone: String,
        val currency: String,
        val products: List<ProductEntity>,
        val menuDayIds: Set<Long>,
        val slogan: String = "",
        val address: String = "",
        val logoUri: String? = null,
        val tables: List<RestaurantTableRecord> = emptyList()
    )

    suspend fun fetchPublicRestaurantsDirectory(): Result<List<PublicRestaurantListing>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=300") { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement restaurants (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<PublicRestaurantListing>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    val bType = o.optString("business_type", "").trim()
                    val isRestoSector = RestaurantDirectoryHelper.isRestaurantShop(bType) ||
                        com.example.util.BusinessTypePresets.getPreset(bType).sectorFeatures.hasTables
                    if (name.isNotBlank() && isRestoSector) {
                        val rawLogo = if (o.has("logo_uri") && !o.isNull("logo_uri")) {
                            o.optString("logo_uri", "").trim().ifBlank { null }
                        } else null
                        list.add(
                            PublicRestaurantListing(
                                shopId = o.optLong("id", 0L),
                                name = name,
                                slogan = o.optString("slogan", "").trim(),
                                city = o.optString("city", "").trim(),
                                address = o.optString("address", "").trim(),
                                phone = o.optString("phone", "").trim(),
                                currency = o.optString("currency", "FCFA").trim().ifBlank { "FCFA" },
                                logoUri = rawLogo,
                                businessType = bType
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MIRROR_RESTO_STAFF = "__CLOUD_RESTO_STAFF__"
        private const val MIRROR_RESTO_TABLE = "__CLOUD_RESTO_TABLE__"
        private const val MIRROR_RESTO_ORDER = "__CLOUD_RESTO_ORDER__"
        private const val MIRROR_RESTO_MENU_DAY = "__CLOUD_RESTO_MENU_DAY__"
    }

    private fun pushRestaurantMirrorEntries(
        shopId: Long,
        markerType: String,
        entries: List<Triple<String, String, Pair<String, Long>>> // actionRouteKey, title, (jsonPayload, timestamp)
    ): Result<Int> {
        if (!SupabaseConfig.isConfigured || entries.isEmpty() || shopId <= 1L) return Result.success(0)
        val arr = JSONArray()
        entries.forEach { (key, title, payloadAndTs) ->
            arr.put(
                JSONObject().apply {
                    put("id", mirrorNotificationId(shopId, markerType, key))
                    put("shop_id", shopId)
                    put("title", title.ifBlank { markerType })
                    put("message", payloadAndTs.first)
                    put("type", markerType)
                    put("priority", "LOW")
                    put("timestamp", payloadAndTs.second)
                    put("is_read", true)
                    put("action_route", key)
                    put("reference_id", shopId)
                }
            )
        }
        val (ok, codeAndBody) = postJsonArrayWithColumnFallback(
            endpoint = "app_notifications?on_conflict=id",
            primaryJson = arr.toString(),
            fallbackJson = null
        )
        return if (ok) Result.success(entries.size)
        else Result.failure(IOException("Échec miroir cloud $markerType (${codeAndBody.first}): ${codeAndBody.second}"))
    }

    private fun fetchRestaurantMirrorPayloads(
        scope: RemoteShopScope,
        markerType: String
    ): Result<List<JSONObject>> {
        if (!SupabaseConfig.isConfigured) return Result.success(emptyList())
        val encType = URLEncoder.encode(markerType, "UTF-8")
        val collected = mutableListOf<JSONObject>()
        val targetIds = if (scope.relatedShopIds.isNotEmpty() && !scope.includeLegacyUnscoped) {
            scope.relatedShopIds
        } else {
            emptySet()
        }

        if (targetIds.isEmpty()) {
            return executeRequest("app_notifications?type=eq.$encType&select=*&limit=2000") { resp ->
                val body = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) {
                    return@executeRequest Result.failure(IOException("Échec lecture miroir $markerType (${resp.code})"))
                }
                val arr = JSONArray(body.ifBlank { "[]" })
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    if (row.optString("type", "") != markerType) continue
                    if (!rowBelongsToScope(row, scope)) continue
                    val msg = row.optString("message", "").trim()
                    if (msg.startsWith("{")) {
                        try {
                            collected.add(JSONObject(msg))
                        } catch (_: Exception) {}
                    }
                }
                Result.success(collected)
            }
        }

        var anyOk = false
        var lastCode = 0
        for (sid in targetIds) {
            executeRequest("app_notifications?shop_id=eq.$sid&type=eq.$encType&select=*&limit=2000") { resp ->
                val body = resp.body?.string() ?: "[]"
                if (resp.isSuccessful) {
                    anyOk = true
                    val arr = JSONArray(body.ifBlank { "[]" })
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONObject(i) ?: continue
                        if (!rowBelongsToScope(row, scope)) continue
                        if (row.optString("type", "") != markerType) continue
                        val msg = row.optString("message", "").trim()
                        if (msg.startsWith("{")) {
                            try {
                                collected.add(JSONObject(msg))
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    lastCode = resp.code
                }
            }
        }
        return if (anyOk) Result.success(collected)
        else Result.failure(IOException("Échec lecture miroir $markerType ($lastCode)"))
    }

    private fun deleteRestaurantMirrorEntry(
        markerType: String,
        actionRouteKey: String,
        shopId: Long = 0L
    ): Result<Unit> {
        if (!SupabaseConfig.isConfigured || actionRouteKey.isBlank()) return Result.success(Unit)
        val encKey = URLEncoder.encode(actionRouteKey, "UTF-8")
        val encType = URLEncoder.encode(markerType, "UTF-8")
        val shopClause = if (shopId > 1L) "&shop_id=eq.$shopId" else ""
        return executeRequest("app_notifications?action_route=eq.$encKey&type=eq.$encType$shopClause", method = "DELETE") { resp ->
            if (resp.isSuccessful) Result.success(Unit)
            else Result.failure(IOException("Échec suppression miroir $markerType (${resp.code})"))
        }
    }

    suspend fun authenticateShopTeamMemberRemote(
        identifier: String,
        pin: String
    ): Result<RemoteStaffLoginResult?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase non configuré."))
        }
        val cleanId = identifier.trim()
        val cleanPin = pin.trim()
        if (cleanId.isBlank() || cleanPin.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Veuillez renseigner l'identifiant (boutique ou téléphone) et le code PIN."))
        }
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=1000") { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Erreur connexion Supabase (${response.code}): $err"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                var firstIdMatchName: String? = null
                val restaurantRows = mutableListOf<Triple<Long, ConnectedShopSession, RemoteShopLoginMatch>>()
                val otherShopRows = mutableListOf<Triple<Long, ConnectedShopSession, RemoteShopLoginMatch>>()

                for (i in 0 until jsonArr.length()) {
                    val o = jsonArr.getJSONObject(i)
                    val cloudId = o.optLong("id", 0L)
                    val session = parseConnectedShopSession(o)
                    val match = RemoteShopLoginMatch(
                        cloudShopId = cloudId,
                        session = session,
                        profile = session.toShopProfileEntity()
                    )
                    if (RestaurantDirectoryHelper.isRestaurantShop(session.businessType) ||
                        com.example.util.BusinessTypePresets.getPreset(session.businessType).sectorFeatures.hasTables
                    ) {
                        restaurantRows.add(Triple(cloudId, session, match))
                    } else {
                        otherShopRows.add(Triple(cloudId, session, match))
                    }
                    val matchesId = MultiShopStorage.identifierMatchesSession(
                        identifier = cleanId,
                        shopName = session.shopName,
                        shopPhone = session.phone,
                        adminName = session.adminName,
                        adminPhone = session.adminPhone,
                        cloudShopId = cloudId
                    )
                    if (matchesId) {
                        if (firstIdMatchName == null) firstIdMatchName = session.shopName
                        val storedPin = if (o.has("admin_pin") && !o.isNull("admin_pin")) {
                            o.optString("admin_pin", "").trim()
                        } else ""
                        val expectedPin = storedPin.ifBlank { "1234" }
                        if (expectedPin == cleanPin) {
                            return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = null))
                        }
                        val staffList = fetchRestaurantStaff(cloudId, session.shopName).getOrDefault(emptyList())
                        val staffMatch = staffList.firstOrNull { it.isActive && it.pin.trim() == cleanPin }
                        if (staffMatch != null) {
                            return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = staffMatch))
                        }
                        val storedCashierPin = if (o.has("cashier_pin") && !o.isNull("cashier_pin")) {
                            o.optString("cashier_pin", "").trim()
                        } else ""
                        if (storedCashierPin.isNotBlank() && storedCashierPin != "0000" && storedCashierPin == cleanPin) {
                            val cashierStaff = RestaurantStaffAccount(
                                id = "STAFF-CSH-$cloudId",
                                shopId = cloudId,
                                name = "Caissier",
                                phone = session.phone,
                                role = RestaurantStaffRole.CASHIER,
                                pin = cleanPin,
                                isActive = true
                            )
                            return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = cashierStaff))
                        }
                    }
                }

                // Fallback multi-utilisateurs : l'utilisateur saisit son propre téléphone ou nom d'équipier
                if (firstIdMatchName == null) {
                    var matchedStaffShopName: String? = null
                    val normId = AgentDeploymentStorage.normalizeShopName(cleanId)
                    for ((cloudId, session, match) in (restaurantRows + otherShopRows)) {
                        val staffList = fetchRestaurantStaff(cloudId, session.shopName).getOrDefault(emptyList())
                        val candidates = staffList.filter { s ->
                            s.isActive && (
                                (s.phone.isNotBlank() && MultiShopStorage.phonesMatch(cleanId, s.phone)) ||
                                    (s.name.isNotBlank() && AgentDeploymentStorage.normalizeShopName(s.name) == normId)
                                )
                        }
                        if (candidates.isNotEmpty()) {
                            if (matchedStaffShopName == null) matchedStaffShopName = session.shopName
                            val exact = candidates.firstOrNull { it.pin.trim() == cleanPin }
                            if (exact != null) {
                                return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = exact))
                            }
                        }
                    }
                    if (matchedStaffShopName != null) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Code PIN équipe incorrect pour « $matchedStaffShopName ».")
                        )
                    }
                }

                if (firstIdMatchName != null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Code PIN gérant ou équipe incorrect pour « $firstIdMatchName ».")
                    )
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushRestaurantStaff(staff: List<RestaurantStaffAccount>, shopId: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || staff.isEmpty() || shopId <= 1L) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            staff.forEach { member ->
                array.put(member.copy(shopId = shopId).toJson())
            }
            executeRequest(
                endpoint = "restaurant_staff?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(staff.size)
                } else if (isMissingTableError(resp.code, errBody)) {
                    pushRestaurantMirrorEntries(
                        shopId = shopId,
                        markerType = MIRROR_RESTO_STAFF,
                        entries = staff.map { m ->
                            val norm = m.copy(shopId = shopId)
                            Triple(norm.id, "${norm.name} (${norm.role.badgeLabel})", norm.toJson().toString() to norm.updatedAt)
                        }
                    )
                } else {
                    Result.failure(IOException("Échec sync équipe (${resp.code}): $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantStaff(shopId: Long, shopName: String? = null): Result<List<RestaurantStaffAccount>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptyList())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val effectiveShopId = if (shopId > 1L) shopId else scope.canonicalShopId.coerceAtLeast(1L)
            val (ok, codeAndArr) = getScopedJsonRows("restaurant_staff", scope, orderClause = "updated_at.asc", limit = 1000)
            if (ok && codeAndArr.second.length() > 0) {
                val arr = codeAndArr.second
                val list = mutableListOf<RestaurantStaffAccount>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantStaffAccount.fromJson(arr.getJSONObject(i)))
                }
                return@withContext Result.success(list)
            }
            val mirrorRes = fetchRestaurantMirrorPayloads(scope, MIRROR_RESTO_STAFF)
            if (mirrorRes.isSuccess) {
                val mirrored = mirrorRes.getOrThrow()
                    .map { RestaurantStaffAccount.fromJson(it).copy(shopId = effectiveShopId) }
                    .sortedBy { it.updatedAt }
                if (mirrored.isNotEmpty() || ok || isMissingTableError(codeAndArr.first, "")) {
                    return@withContext Result.success(mirrored)
                }
            }
            if (ok) {
                Result.success(emptyList())
            } else {
                Result.failure(IOException("Échec chargement équipe (${codeAndArr.first})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRestaurantStaff(staffId: String, shopId: Long = 0L): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || staffId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(staffId, "UTF-8")
            executeRequest("restaurant_staff?id=eq.$enc", method = "DELETE") { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else if (isMissingTableError(resp.code, errBody)) {
                    deleteRestaurantMirrorEntry(MIRROR_RESTO_STAFF, staffId, shopId)
                } else {
                    Result.failure(IOException("Échec suppression membre (${resp.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushRestaurantTables(tables: List<RestaurantTableRecord>, shopId: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || tables.isEmpty() || shopId <= 1L) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            tables.forEach { t -> array.put(t.copy(shopId = shopId).toJson()) }
            executeRequest(
                endpoint = "restaurant_tables?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(tables.size)
                } else if (isMissingTableError(resp.code, errBody)) {
                    pushRestaurantMirrorEntries(
                        shopId = shopId,
                        markerType = MIRROR_RESTO_TABLE,
                        entries = tables.map { t ->
                            val norm = t.copy(shopId = shopId)
                            Triple(norm.id, norm.tableNumber, norm.toJson().toString() to norm.updatedAt)
                        }
                    )
                } else {
                    Result.failure(IOException("Échec sync tables (${resp.code}): $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantTables(shopId: Long, shopName: String? = null): Result<List<RestaurantTableRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptyList())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val effectiveShopId = if (shopId > 1L) shopId else scope.canonicalShopId.coerceAtLeast(1L)
            val (ok, codeAndArr) = getScopedJsonRows("restaurant_tables", scope, orderClause = "table_number.asc", limit = 1000)
            if (ok && codeAndArr.second.length() > 0) {
                val arr = codeAndArr.second
                val list = mutableListOf<RestaurantTableRecord>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantTableRecord.fromJson(arr.getJSONObject(i)))
                }
                return@withContext Result.success(list)
            }
            val mirrorRes = fetchRestaurantMirrorPayloads(scope, MIRROR_RESTO_TABLE)
            if (mirrorRes.isSuccess) {
                val mirrored = mirrorRes.getOrThrow()
                    .map { RestaurantTableRecord.fromJson(it).copy(shopId = effectiveShopId) }
                    .sortedBy { it.tableNumber }
                if (mirrored.isNotEmpty() || ok || isMissingTableError(codeAndArr.first, "")) {
                    return@withContext Result.success(mirrored)
                }
            }
            if (ok) {
                Result.success(emptyList())
            } else {
                Result.failure(IOException("Échec chargement tables (${codeAndArr.first})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRestaurantTable(tableId: String, shopId: Long = 0L): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || tableId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(tableId, "UTF-8")
            executeRequest("restaurant_tables?id=eq.$enc", method = "DELETE") { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else if (isMissingTableError(resp.code, errBody)) {
                    deleteRestaurantMirrorEntry(MIRROR_RESTO_TABLE, tableId, shopId)
                } else {
                    Result.failure(IOException("Échec suppression table (${resp.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushRestaurantOrders(orders: List<RestaurantOrderRecord>, shopId: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || orders.isEmpty() || shopId <= 1L) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            orders.forEach { order ->
                array.put(order.copy(shopId = shopId).toJson())
            }
            executeRequest(
                endpoint = "restaurant_orders?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(orders.size)
                } else if (isMissingTableError(resp.code, errBody)) {
                    pushRestaurantMirrorEntries(
                        shopId = shopId,
                        markerType = MIRROR_RESTO_ORDER,
                        entries = orders.map { ord ->
                            val norm = ord.copy(shopId = shopId)
                            Triple(norm.id, "${norm.orderNumber} (${norm.tableName})", norm.toJson().toString() to norm.updatedAt)
                        }
                    )
                } else {
                    Result.failure(IOException("Échec sync commandes (${resp.code}): $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantOrders(shopId: Long, shopName: String? = null): Result<List<RestaurantOrderRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptyList())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val effectiveShopId = if (shopId > 1L) shopId else scope.canonicalShopId.coerceAtLeast(1L)
            val (ok, codeAndArr) = getScopedJsonRows("restaurant_orders", scope, orderClause = "created_at.desc", limit = 200)
            if (ok && codeAndArr.second.length() > 0) {
                val arr = codeAndArr.second
                val list = mutableListOf<RestaurantOrderRecord>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantOrderRecord.fromJson(arr.getJSONObject(i)))
                }
                return@withContext Result.success(list)
            }
            val mirrorRes = fetchRestaurantMirrorPayloads(scope, MIRROR_RESTO_ORDER)
            if (mirrorRes.isSuccess) {
                val mirrored = mirrorRes.getOrThrow()
                    .map { RestaurantOrderRecord.fromJson(it).copy(shopId = effectiveShopId) }
                    .sortedByDescending { it.createdAt }
                if (mirrored.isNotEmpty() || ok || isMissingTableError(codeAndArr.first, "")) {
                    return@withContext Result.success(mirrored)
                }
            }
            if (ok) {
                Result.success(emptyList())
            } else {
                Result.failure(IOException("Échec chargement commandes (${codeAndArr.first})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushMenuOfDay(shopId: Long, productIds: Set<Long>): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(Unit)
        try {
            val idsArr = JSONArray().apply {
                productIds.filter { it > 0L }.forEach { put(it) }
            }
            val now = System.currentTimeMillis()
            val menuObj = JSONObject().apply {
                put("shop_id", shopId)
                put("product_ids_json", idsArr.toString())
                put("updated_at", now)
            }
            val payload = JSONArray().apply { put(menuObj) }
            executeRequest(
                endpoint = "restaurant_menu_day?on_conflict=shop_id",
                method = "POST",
                bodyString = payload.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                val errBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else if (isMissingTableError(resp.code, errBody)) {
                    pushRestaurantMirrorEntries(
                        shopId = shopId,
                        markerType = MIRROR_RESTO_MENU_DAY,
                        entries = listOf(Triple("MENU_DAY_$shopId", "Menu du jour", menuObj.toString() to now))
                    ).map { Unit }
                } else {
                    Result.failure(IOException("Échec sync menu du jour (${resp.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractMenuDayProductIdsFromJson(obj: JSONObject): Set<Long> {
        val out = linkedSetOf<Long>()
        fun addRawId(anyVal: Any?) {
            when (anyVal) {
                is Number -> anyVal.toLong().takeIf { it > 0L }?.let { out.add(it) }
                is String -> anyVal.trim().toLongOrNull()?.takeIf { it > 0L }?.let { out.add(it) }
                is JSONObject -> {
                    val v = anyVal.optLong("product_id", 0L).takeIf { it > 0L } ?: anyVal.optLong("id", 0L)
                    if (v > 0L) out.add(v)
                }
            }
        }
        fun parseArrayField(field: String) {
            if (!obj.has(field) || obj.isNull(field)) return
            val raw = obj.opt(field)
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) addRawId(raw.opt(i))
                }
                is String -> {
                    val clean = raw.trim()
                    if (clean.startsWith("[")) {
                        try {
                            val arr = JSONArray(clean)
                            for (i in 0 until arr.length()) addRawId(arr.opt(i))
                        } catch (_: Exception) {}
                    } else if (clean.isNotBlank()) {
                        clean.split(",").forEach { part -> addRawId(part) }
                    }
                }
            }
        }
        parseArrayField("product_ids_json")
        parseArrayField("menu_item_ids")
        parseArrayField("dish_ids")
        obj.optLong("product_id", 0L).takeIf { it > 0L }?.let { out.add(it) }
        obj.optLong("menu_item_id", 0L).takeIf { it > 0L }?.let { out.add(it) }
        val prodsArr = obj.optJSONArray("products")
        if (prodsArr != null) {
            for (i in 0 until prodsArr.length()) addRawId(prodsArr.opt(i))
        } else {
            obj.optJSONObject("products")?.let { addRawId(it) }
        }
        val menuItemsArr = obj.optJSONArray("menu_items")
        if (menuItemsArr != null) {
            for (i in 0 until menuItemsArr.length()) addRawId(menuItemsArr.opt(i))
        }
        return out
    }

    private fun fetchRestaurantMenuItemsTable(scope: RemoteShopScope): Pair<Set<Long>, List<ProductEntity>> {
        val ids = linkedSetOf<Long>()
        val standaloneProducts = mutableListOf<ProductEntity>()
        try {
            val (ok, codeAndArr) = getScopedJsonRows("restaurant_menu_items", scope, orderClause = null, limit = 1000)
            if (ok) {
                val arr = codeAndArr.second
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val linkedProdId = o.optLong("product_id", 0L).takeIf { it > 0L }
                    if (linkedProdId != null) {
                        ids.add(linkedProdId)
                    } else if (o.optString("name", "").isNotBlank()) {
                        val itemId = o.optLong("id", 0L).takeIf { it > 0L } ?: (1_000_000L + i)
                        ids.add(itemId)
                        val galleryJson = o.optString("gallery_images_json", "[]").ifBlank { "[]" }
                        val imgUri = o.optString("image_uri", "").trim()
                            .ifBlank { extractFirstImageFromGalleryJson(galleryJson) }
                        standaloneProducts.add(
                            ProductEntity(
                                id = itemId,
                                name = o.optString("name", "Plat").trim(),
                                category = o.optString("category", "Plats").ifBlank { "Plats" },
                                sellPrice = o.optLong("sell_price", o.optLong("price", 0L)),
                                buyPrice = o.optLong("buy_price", 0L),
                                stockQuantity = o.optInt("stock_quantity", 50),
                                minStockAlert = o.optInt("min_stock_alert", 5),
                                barcode = o.optString("barcode", ""),
                                unit = o.optString("unit", "Plat").ifBlank { "Plat" },
                                iconName = o.optString("icon_name", "restaurant"),
                                isAvailable = o.optBoolean("is_available", true),
                                imageUri = imgUri,
                                galleryImagesJson = galleryJson
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return ids to standaloneProducts
    }

    suspend fun fetchMenuOfDay(shopId: Long, shopName: String? = null): Result<Set<Long>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptySet())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val collectedIds = linkedSetOf<Long>()
            val (ok, codeAndArr) = getScopedJsonRows("restaurant_menu_day", scope, orderClause = null, limit = 500)
            if (ok && codeAndArr.second.length() > 0) {
                val arr = codeAndArr.second
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { collectedIds.addAll(extractMenuDayProductIdsFromJson(it)) }
                }
            }
            if (collectedIds.isEmpty()) {
                val mirrorRes = fetchRestaurantMirrorPayloads(scope, MIRROR_RESTO_MENU_DAY)
                if (mirrorRes.isSuccess) {
                    val latest = mirrorRes.getOrThrow().maxByOrNull { it.optLong("updated_at", 0L) }
                    if (latest != null) {
                        collectedIds.addAll(extractMenuDayProductIdsFromJson(latest))
                    }
                }
            }
            val (menuItemIds, _) = fetchRestaurantMenuItemsTable(scope)
            collectedIds.addAll(menuItemIds)
            if (collectedIds.isNotEmpty() || ok || isMissingTableError(codeAndArr.first, "")) {
                Result.success(collectedIds)
            } else {
                Result.failure(IOException("Échec lecture menu du jour (${codeAndArr.first})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun productIdMatchesMenuDay(
        productId: Long,
        menuDayIds: Set<Long>,
        shopIds: Set<Long>
    ): Boolean {
        if (productId in menuDayIds) return true
        for (sid in shopIds) {
            if (sid > 1L && MultiShopStorage.scopedCloudId(sid, productId) in menuDayIds) {
                return true
            }
        }
        if (productId >= 1_000_000_000_000L) {
            val raw = productId % 1_000_000L
            if (raw in menuDayIds && shopIds.any { it > 1L && MultiShopStorage.scopedCloudId(it, raw) == productId }) {
                return true
            }
        }
        return false
    }

    suspend fun fetchPublicRestaurantMenuBundle(
        shopId: Long,
        fallbackShopName: String = ""
    ): Result<PublicRestaurantMenuBundle> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Connexion Cloud non configurée."))
        }
        try {
            var resolvedShop: ShopProfileEntity? = null
            var resolvedShopId = shopId
            val matchingShopIds = linkedSetOf<Long>()
            if (resolvedShopId > 1L) matchingShopIds.add(resolvedShopId)

            if (resolvedShopId > 1L) {
                executeRequest("shop_profile?id=eq.$resolvedShopId&select=*&limit=1") { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        if (arr.length() > 0) {
                            resolvedShop = parseShopProfile(arr.getJSONObject(0))
                        }
                    }
                }
            }
            val lookupName = resolvedShop?.name?.takeIf { it.isNotBlank() } ?: fallbackShopName
            if (lookupName.isNotBlank()) {
                executeRequest("shop_profile?select=*&order=updated_at.desc&limit=500") { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val targetNorm = AgentDeploymentStorage.normalizeShopName(lookupName)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val candidateName = AgentDeploymentStorage.normalizeShopName(obj.optString("name", ""))
                            if (candidateName == targetNorm) {
                                val candId = obj.optLong("id", 0L)
                                if (candId > 1L) matchingShopIds.add(candId)
                                if (resolvedShop == null) {
                                    resolvedShop = parseShopProfile(obj)
                                    if (candId > 1L) resolvedShopId = candId
                                }
                            }
                        }
                    }
                }
            }

            if (resolvedShopId <= 1L && resolvedShop == null) {
                return@withContext Result.failure(IllegalStateException("Restaurant introuvable."))
            }

            val effectiveShopId = resolvedShopId.takeIf { it > 1L }
                ?: matchingShopIds.firstOrNull()
                ?: resolvedShopId
            val scope = resolveRemoteShopScope(effectiveShopId, lookupName)
            val prodRes = fetchProducts(effectiveShopId, shopName = lookupName)
            val (menuItemIdsFromTable, standaloneMenuItems) = fetchRestaurantMenuItemsTable(scope)
            if (prodRes.isFailure && standaloneMenuItems.isEmpty()) {
                return@withContext Result.failure(
                    prodRes.exceptionOrNull() ?: IOException("Impossible de charger la carte du restaurant.")
                )
            }
            val mergedByName = LinkedHashMap<String, ProductEntity>()
            prodRes.getOrDefault(emptyList()).forEach { p ->
                mergedByName[p.name.trim().lowercase()] = p
            }
            standaloneMenuItems.forEach { extra ->
                val key = extra.name.trim().lowercase()
                if (!mergedByName.containsKey(key)) {
                    mergedByName[key] = extra
                }
            }
            val products = mergedByName.values.toList()

            val menuDayIds = (fetchMenuOfDay(effectiveShopId, shopName = lookupName).getOrDefault(emptySet()) + menuItemIdsFromTable)
            val tables = fetchRestaurantTables(effectiveShopId, shopName = lookupName).getOrDefault(emptyList())
            val allCandidateShopIds = matchingShopIds + scope.relatedShopIds + effectiveShopId

            val filtered = if (menuDayIds.isNotEmpty()) {
                val matched = products.filter { p ->
                    p.isAvailable && productIdMatchesMenuDay(p.id, menuDayIds, allCandidateShopIds)
                }
                if (matched.isNotEmpty()) matched else products.filter { it.isAvailable }
            } else {
                products.filter { it.isAvailable }
            }

            Result.success(
                PublicRestaurantMenuBundle(
                    shopId = effectiveShopId,
                    shopName = resolvedShop?.name?.takeIf { it.isNotBlank() } ?: fallbackShopName.ifBlank { "Restaurant" },
                    city = resolvedShop?.city.orEmpty(),
                    phone = resolvedShop?.phone.orEmpty(),
                    currency = resolvedShop?.currency?.ifBlank { "FCFA" } ?: "FCFA",
                    products = filtered,
                    menuDayIds = menuDayIds,
                    slogan = resolvedShop?.slogan.orEmpty(),
                    address = resolvedShop?.address.orEmpty(),
                    logoUri = resolvedShop?.logoUri,
                    tables = tables
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- COUTURE DIRECTORY, CATALOGUE QR, MESURES & SUIVI CLIENT ---

    data class PublicCoutureCatalogBundle(
        val shopId: Long,
        val shopName: String,
        val slogan: String = "",
        val city: String = "",
        val address: String = "",
        val phone: String = "",
        val currency: String = "FCFA",
        val logoUri: String? = null,
        val models: List<CoutureModelRecord> = emptyList(),
        val products: List<ProductEntity> = emptyList()
    )

    suspend fun fetchPublicCouturiersDirectory(): Result<List<PublicCouturierListing>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("shop_profile?select=*&order=updated_at.desc&limit=300") { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement couturiers (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<PublicCouturierListing>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    val bType = o.optString("business_type", "").trim()
                    if (name.isNotBlank() && CoutureDirectoryHelper.isCoutureShop(bType)) {
                        val rawLogo = if (o.has("logo_uri") && !o.isNull("logo_uri")) {
                            o.optString("logo_uri", "").trim().ifBlank { null }
                        } else null
                        list.add(
                            PublicCouturierListing(
                                shopId = o.optLong("id", 0L),
                                name = name,
                                slogan = o.optString("slogan", "").trim(),
                                city = o.optString("city", "").trim(),
                                address = o.optString("address", "").trim(),
                                phone = o.optString("phone", "").trim(),
                                currency = o.optString("currency", "FCFA").trim().ifBlank { "FCFA" },
                                logoUri = rawLogo,
                                businessType = bType
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPublicCoutureModelsFeed(): Result<List<CoutureModelRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("couture_models?is_available=eq.true&select=*&order=updated_at.desc&limit=300") { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement modèles couture (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = (0 until arr.length()).map { CoutureModelRecord.fromJson(arr.getJSONObject(it)) }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushCoutureModels(models: List<CoutureModelRecord>, shopId: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || models.isEmpty() || shopId <= 1L) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            models.forEach { array.put(it.copy(shopId = shopId).toJson()) }
            executeRequest(
                endpoint = "couture_models?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(models.size)
                else Result.failure(IOException("Échec sync modèles couture (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCoutureModels(shopId: Long, shopName: String? = null): Result<List<CoutureModelRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptyList())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val (ok, codeAndArr) = getScopedJsonRows("couture_models", scope, orderClause = "updated_at.desc", limit = 500)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec chargement modèles (${codeAndArr.first})"))
            }
            val arr = codeAndArr.second
            Result.success((0 until arr.length()).map { CoutureModelRecord.fromJson(arr.getJSONObject(it)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCoutureModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || modelId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(modelId, "UTF-8")
            executeRequest("couture_models?id=eq.$enc", method = "DELETE") { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec suppression modèle (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushCoutureOrders(orders: List<CoutureOrderRecord>, shopId: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || orders.isEmpty() || shopId <= 1L) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            orders.forEach { array.put(it.copy(shopId = shopId).toJson()) }
            executeRequest(
                endpoint = "couture_orders?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(orders.size)
                else Result.failure(IOException("Échec sync fiches couture (${resp.code}): ${resp.body?.string() ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCoutureOrders(shopId: Long, shopName: String? = null): Result<List<CoutureOrderRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || (shopId <= 0L && shopName.isNullOrBlank())) {
            return@withContext Result.success(emptyList())
        }
        try {
            val scope = resolveRemoteShopScope(shopId, shopName)
            val (ok, codeAndArr) = getScopedJsonRows("couture_orders", scope, orderClause = "updated_at.desc", limit = 300)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec chargement fiches couture (${codeAndArr.first})"))
            }
            val arr = codeAndArr.second
            Result.success((0 until arr.length()).map { CoutureOrderRecord.fromJson(arr.getJSONObject(it)) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchClientCoutureOrdersByPhone(phone: String): Result<List<CoutureOrderRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        val digits = CoutureDirectoryHelper.normalizePhone(phone)
        if (digits.isBlank()) return@withContext Result.success(emptyList())
        try {
            val tail = if (digits.length > 8) digits.takeLast(8) else digits
            val enc = URLEncoder.encode("*$tail*", "UTF-8")
            executeRequest("couture_orders?customer_phone=ilike.$enc&select=*&order=updated_at.desc&limit=100") { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec suivi couture (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                Result.success((0 until arr.length()).map { CoutureOrderRecord.fromJson(arr.getJSONObject(it)) })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushCoutureClientAccount(account: CoutureClientAccount): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(Unit)
        try {
            val arr = JSONArray().apply { put(account.toJson()) }
            executeRequest(
                endpoint = "couture_clients?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec sync compte client (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPublicCoutureCatalogBundle(
        shopId: Long,
        fallbackShopName: String = ""
    ): Result<PublicCoutureCatalogBundle> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Connexion Cloud non configurée."))
        }
        try {
            var resolvedShop: ShopProfileEntity? = null
            var resolvedShopId = shopId

            if (resolvedShopId > 1L) {
                executeRequest("shop_profile?id=eq.$resolvedShopId&select=*&limit=1") { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        if (arr.length() > 0) {
                            resolvedShop = parseShopProfile(arr.getJSONObject(0))
                        }
                    }
                }
            }
            val lookupName = resolvedShop?.name?.takeIf { it.isNotBlank() } ?: fallbackShopName
            if (resolvedShop == null && lookupName.isNotBlank()) {
                executeRequest("shop_profile?select=*&order=updated_at.desc&limit=500") { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val targetNorm = AgentDeploymentStorage.normalizeShopName(lookupName)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val candidateName = AgentDeploymentStorage.normalizeShopName(obj.optString("name", ""))
                            if (candidateName == targetNorm) {
                                resolvedShop = parseShopProfile(obj)
                                resolvedShopId = obj.optLong("id", resolvedShopId)
                                break
                            }
                        }
                    }
                }
            }

            if (resolvedShopId <= 1L && resolvedShop == null) {
                return@withContext Result.failure(IllegalStateException("Atelier couture introuvable."))
            }

            val finalShopName = resolvedShop?.name?.takeIf { it.isNotBlank() } ?: lookupName
            val models = fetchCoutureModels(resolvedShopId, finalShopName).getOrDefault(emptyList()).filter { it.isAvailable }
            val products = fetchProducts(resolvedShopId, finalShopName).getOrDefault(emptyList()).filter { it.isAvailable }

            Result.success(
                PublicCoutureCatalogBundle(
                    shopId = resolvedShopId,
                    shopName = finalShopName.ifBlank { "Atelier Couture" },
                    slogan = resolvedShop?.slogan.orEmpty(),
                    city = resolvedShop?.city.orEmpty(),
                    address = resolvedShop?.address.orEmpty(),
                    phone = resolvedShop?.phone.orEmpty(),
                    currency = resolvedShop?.currency?.ifBlank { "FCFA" } ?: "FCFA",
                    logoUri = resolvedShop?.logoUri,
                    models = models,
                    products = products
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- GESTION POUBELLES / SALUBRITÉ (ÉBOUEURS & FAMILLES) ---

    suspend fun pushWasteBusinesses(list: List<WasteBusinessProfile>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "waste_businesses?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync business salubrité (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWasteBusinesses(): Result<List<WasteBusinessProfile>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("waste_businesses?select=*&order=updated_at.desc&limit=500") { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture business salubrité (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { WasteBusinessProfile.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushWasteClients(list: List<WasteHouseholdClient>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "waste_clients?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync clients poubelles (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWasteClients(businessId: String? = null): Result<List<WasteHouseholdClient>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val cleanBiz = businessId?.trim()?.takeIf { it.isNotBlank() }
            val endpoint = if (cleanBiz != null) {
                val enc = java.net.URLEncoder.encode(cleanBiz, "UTF-8")
                "waste_clients?business_id=eq.$enc&select=*&order=updated_at.desc&limit=1000"
            } else {
                "waste_clients?select=*&order=updated_at.desc&limit=1000"
            }
            executeRequest(endpoint) { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture clients poubelles (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { WasteHouseholdClient.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteWasteClient(clientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || clientId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = java.net.URLEncoder.encode(clientId, "UTF-8")
            executeRequest(endpoint = "waste_clients?id=eq.$enc", method = "DELETE") { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Erreur suppression client poubelles (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- TONTINE NUMÉRIQUE (BUSINESS, GROUPES & CARTES 30 JOURS) ---

    suspend fun pushTontineBusinesses(list: List<TontineBusinessProfile>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "tontine_businesses?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync business tontine (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTontineBusinesses(): Result<List<TontineBusinessProfile>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("tontine_businesses?select=*&order=updated_at.desc&limit=500") { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture business tontine (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { TontineBusinessProfile.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushTontineGroups(list: List<TontineGroup>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "tontine_groups?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync groupes tontine (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTontineGroups(businessId: String? = null): Result<List<TontineGroup>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val cleanBiz = businessId?.trim()?.takeIf { it.isNotBlank() }
            val endpoint = if (cleanBiz != null) {
                val enc = java.net.URLEncoder.encode(cleanBiz, "UTF-8")
                "tontine_groups?business_id=eq.$enc&select=*&order=updated_at.desc&limit=1000"
            } else {
                "tontine_groups?select=*&order=updated_at.desc&limit=1000"
            }
            executeRequest(endpoint) { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture groupes tontine (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { TontineGroup.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushTontineCards(list: List<TontineMemberCard>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "tontine_cards?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync cartes tontine (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTontineCards(businessId: String? = null): Result<List<TontineMemberCard>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val cleanBiz = businessId?.trim()?.takeIf { it.isNotBlank() }
            val endpoint = if (cleanBiz != null) {
                val enc = java.net.URLEncoder.encode(cleanBiz, "UTF-8")
                "tontine_cards?business_id=eq.$enc&select=*&order=updated_at.desc&limit=1500"
            } else {
                "tontine_cards?select=*&order=updated_at.desc&limit=1500"
            }
            executeRequest(endpoint) { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture cartes tontine (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { TontineMemberCard.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTontineCard(cardId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || cardId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = java.net.URLEncoder.encode(cardId, "UTF-8")
            executeRequest(endpoint = "tontine_cards?id=eq.$enc", method = "DELETE") { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Erreur suppression carte tontine (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- UTILISATEURS PUBLICS (IDENTIFICATION & COMPTE CLIENT) ---

    suspend fun pushAppUsers(list: List<PublicUserAccount>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || list.isEmpty()) return@withContext Result.success(0)
        try {
            val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
            executeRequest(
                endpoint = "app_users?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            ) { resp ->
                if (resp.isSuccessful) Result.success(list.size)
                else Result.failure(IOException("Échec sync utilisateur (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAppUsers(): Result<List<PublicUserAccount>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            executeRequest("app_users?select=*&order=updated_at.desc&limit=500") { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("Erreur lecture utilisateurs (${resp.code})"))
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = (0 until arr.length()).map { PublicUserAccount.fromJson(arr.getJSONObject(it)) }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
