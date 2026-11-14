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

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        bodyString: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val url = "${SupabaseConfig.restBaseUrl}/$endpoint"
        val authToken = SupabaseAuthManager.currentToken ?: SupabaseConfig.rawAnonKey
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

    private fun postJsonArrayWithColumnFallback(
        endpoint: String,
        primaryJson: String,
        fallbackJson: String? = null
    ): Pair<Boolean, Pair<Int, String>> {
        val request = buildRequest(
            endpoint = endpoint,
            method = "POST",
            bodyString = primaryJson,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
        )
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true to (response.code to body)
            }
            if (fallbackJson != null && response.code == 400 &&
                (body.contains("column", ignoreCase = true) || body.contains("PGRST204") || body.contains("schema cache"))
            ) {
                val retryReq = buildRequest(
                    endpoint = endpoint,
                    method = "POST",
                    bodyString = fallbackJson,
                    extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
                )
                client.newCall(retryReq).execute().use { retryResp ->
                    val retryBody = retryResp.body?.string() ?: ""
                    return retryResp.isSuccessful to (retryResp.code to retryBody)
                }
            }
            return false to (response.code to body)
        }
    }

    private fun getWithShopFilterFallback(
        table: String,
        shopId: Long?,
        orderClause: String? = null,
        limit: Int = 2000
    ): Pair<Boolean, Pair<Int, String>> {
        val orderPart = if (!orderClause.isNullOrBlank()) "&order=$orderClause" else ""
        val primaryEndpoint = if (shopId != null && shopId > 1L) {
            "$table?shop_id=eq.$shopId&select=*$orderPart&limit=$limit"
        } else {
            "$table?select=*$orderPart&limit=$limit"
        }
        client.newCall(buildRequest(primaryEndpoint)).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true to (response.code to body)
            }
            if (shopId != null && shopId > 1L && response.code == 400 &&
                (body.contains("shop_id", ignoreCase = true) || body.contains("column", ignoreCase = true))
            ) {
                val fallbackEndpoint = "$table?select=*$orderPart&limit=$limit"
                client.newCall(buildRequest(fallbackEndpoint)).execute().use { fbResp ->
                    val fbBody = fbResp.body?.string() ?: ""
                    return fbResp.isSuccessful to (fbResp.code to fbBody)
                }
            }
            return false to (response.code to body)
        }
    }

    // --- PRODUCTS ---

    suspend fun pushProducts(products: List<ProductEntity>, shopId: Long = 0L): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || products.isEmpty()) return@withContext Result.success(0)
        try {
            val primaryArray = JSONArray()
            val fallbackArray = JSONArray()
            products.forEach { p ->
                val cloudId = MultiShopStorage.scopedCloudId(shopId, p.id)
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
                    put("image_uri", p.imageUri)
                }
                fallbackArray.put(JSONObject(baseObj.toString()))
                baseObj.put("shop_id", shopId)
                baseObj.put("gallery_images_json", p.galleryImagesJson)
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
            val request = buildRequest(
                endpoint = "products?id=eq.$cloudId",
                method = "DELETE"
            )
            client.newCall(request).execute().use { response ->
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

    suspend fun fetchProducts(shopId: Long? = null): Result<List<ProductEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("products", shopId, orderClause = "name.asc", limit = 2000)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération produits: ${codeAndBody.first}"))
            }
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<ProductEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
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
                        imageUri = o.optString("image_uri", ""),
                        galleryImagesJson = o.optString("gallery_images_json", "[]").ifBlank { "[]" }
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

    suspend fun fetchCustomers(shopId: Long? = null): Result<List<CustomerEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("customers", shopId, orderClause = "total_spent.desc", limit = 2000)
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

    suspend fun fetchSales(shopId: Long): Result<List<SaleEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("sales", shopId, orderClause = "timestamp.desc", limit = 2000)
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

    suspend fun fetchSaleItems(shopId: Long, validSaleIds: Set<Long>? = null): Result<List<SaleItemEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("sale_items", shopId, orderClause = "id.desc", limit = 5000)
            if (!ok) {
                return@withContext Result.failure(IOException("Échec récupération détails ventes: ${codeAndBody.first}"))
            }
            val jsonArr = JSONArray(codeAndBody.second.ifBlank { "[]" })
            val list = mutableListOf<SaleItemEntity>()
            for (i in 0 until jsonArr.length()) {
                val o = jsonArr.getJSONObject(i)
                val saleIdVal = o.optLong("sale_id", 0L)
                if (validSaleIds != null && saleIdVal !in validSaleIds) continue
                list.add(
                    SaleItemEntity(
                        id = o.optLong("id", 0L),
                        saleId = saleIdVal,
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
            val req = buildRequest("shop_profile?select=id,name,deployer_id&limit=1000")
            client.newCall(req).execute().use { resp ->
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
                    val fallbackReq = buildRequest("shop_profile?select=id,name&limit=1000")
                    client.newCall(fallbackReq).execute().use { fallbackResp ->
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
            val req = buildRequest("agent_deployments?select=id,shop_name&limit=1000")
            client.newCall(req).execute().use { resp ->
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
            val request = buildRequest("shop_profile?deployer_id=eq.$encoded&order=updated_at.desc")
            client.newCall(request).execute().use { response ->
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
            o.optString("logo_uri", "").trim().ifBlank { null }
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=1000")
            client.newCall(request).execute().use { response ->
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=200")
            client.newCall(request).execute().use { response ->
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
        val cleanDeployerId = profile.deployerId?.trim()?.uppercase()
        if (cleanDeployerId.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Un compte Agent Déployeur est obligatoire pour créer ou synchroniser une boutique.")
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
                if (includeLogo && !profile.logoUri.isNullOrBlank()) {
                    put("logo_uri", profile.logoUri)
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
                val request = buildRequest(
                    endpoint = "shop_profile?on_conflict=id",
                    method = "POST",
                    bodyString = array.toString(),
                    extraHeaders = mapOf(
                        "Prefer" to "resolution=merge-duplicates"
                    )
                )
                return client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    response.isSuccessful to (response.code to body)
                }
            }

            var includeLogo = !profile.logoUri.isNullOrBlank()
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=1")
            client.newCall(request).execute().use { response ->
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

    suspend fun fetchSuppliers(shopId: Long): Result<List<SupplierEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("suppliers", shopId, orderClause = "name.asc", limit = 2000)
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

    suspend fun fetchPurchases(shopId: Long): Result<List<PurchaseEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("purchases", shopId, orderClause = "timestamp.desc", limit = 2000)
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

    suspend fun fetchCashMovements(shopId: Long): Result<List<CashMovementEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("cash_movements", shopId, orderClause = "timestamp.desc", limit = 2000)
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

    suspend fun fetchCashSessions(shopId: Long): Result<List<CashSessionEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val (ok, codeAndBody) = getWithShopFilterFallback("cash_sessions", shopId, orderClause = "opened_at.desc", limit = 500)
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
                val existingReq = buildRequest("agent_deployments?select=id,shop_name&limit=1000")
                client.newCall(existingReq).execute().use { existingResp ->
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
            val request = buildRequest(
                endpoint = "agent_deployments?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(endpoint)
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest("agents?select=*&order=id.asc")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "agents",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest("shop_ads?select=*&order=updated_at.desc&limit=200")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "shop_ads?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "shop_ads?id=eq.$encodedId",
                method = "DELETE"
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec suppression publicité (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAdMedia(
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
            val cleanPath = remotePath.trimStart('/')
            val uploadUrl = "$cleanBase/storage/v1/object/shop-ads/$cleanPath"
            val authToken = SupabaseAuthManager.currentToken ?: SupabaseConfig.rawAnonKey
            val mediaType = contentType.toMediaType()
            val req = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", SupabaseConfig.rawAnonKey)
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("x-upsert", "true")
                .post(file.asRequestBody(mediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val publicUrl = "$cleanBase/storage/v1/object/public/shop-ads/$cleanPath"
                    Result.success(publicUrl)
                } else {
                    val err = resp.body?.string() ?: ""
                    Result.failure(IOException("Échec upload média (${resp.code}): $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=300")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement restaurants (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<PublicRestaurantListing>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    val bType = o.optString("business_type", "").trim()
                    if (name.isNotBlank() && RestaurantDirectoryHelper.isRestaurantShop(bType)) {
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=1000")
            client.newCall(request).execute().use { response ->
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
                        if (firstIdMatchName == null) firstIdMatchName = session.shopName
                        val storedPin = if (o.has("admin_pin") && !o.isNull("admin_pin")) {
                            o.optString("admin_pin", "").trim()
                        } else ""
                        val expectedPin = storedPin.ifBlank { "1234" }
                        val match = RemoteShopLoginMatch(
                            cloudShopId = cloudId,
                            session = session,
                            profile = session.toShopProfileEntity()
                        )
                        if (expectedPin == cleanPin) {
                            return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = null))
                        }
                        val staffList = fetchRestaurantStaff(cloudId).getOrDefault(emptyList())
                        val staffMatch = staffList.firstOrNull { it.isActive && it.pin == cleanPin }
                        if (staffMatch != null) {
                            return@withContext Result.success(RemoteStaffLoginResult(match = match, staff = staffMatch))
                        }
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
            val request = buildRequest(
                endpoint = "restaurant_staff?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(staff.size)
                else Result.failure(IOException("Échec sync équipe (${resp.code}): ${resp.body?.string() ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantStaff(shopId: Long): Result<List<RestaurantStaffAccount>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("restaurant_staff?shop_id=eq.$shopId&select=*&order=updated_at.asc")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement équipe (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<RestaurantStaffAccount>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantStaffAccount.fromJson(arr.getJSONObject(i)))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRestaurantStaff(staffId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || staffId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(staffId, "UTF-8")
            val request = buildRequest("restaurant_staff?id=eq.$enc", method = "DELETE")
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec suppression membre (${resp.code})"))
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
            val request = buildRequest(
                endpoint = "restaurant_tables?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(tables.size)
                else Result.failure(IOException("Échec sync tables (${resp.code}): ${resp.body?.string() ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantTables(shopId: Long): Result<List<RestaurantTableRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("restaurant_tables?shop_id=eq.$shopId&select=*&order=table_number.asc")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement tables (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<RestaurantTableRecord>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantTableRecord.fromJson(arr.getJSONObject(i)))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRestaurantTable(tableId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || tableId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(tableId, "UTF-8")
            val request = buildRequest("restaurant_tables?id=eq.$enc", method = "DELETE")
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec suppression table (${resp.code})"))
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
            val request = buildRequest(
                endpoint = "restaurant_orders?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(orders.size)
                else Result.failure(IOException("Échec sync commandes (${resp.code}): ${resp.body?.string() ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRestaurantOrders(shopId: Long): Result<List<RestaurantOrderRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("restaurant_orders?shop_id=eq.$shopId&select=*&order=created_at.desc&limit=200")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement commandes (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<RestaurantOrderRecord>()
                for (i in 0 until arr.length()) {
                    list.add(RestaurantOrderRecord.fromJson(arr.getJSONObject(i)))
                }
                Result.success(list)
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
            val payload = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("shop_id", shopId)
                        put("product_ids_json", idsArr.toString())
                        put("updated_at", System.currentTimeMillis())
                    }
                )
            }
            val request = buildRequest(
                endpoint = "restaurant_menu_day?on_conflict=shop_id",
                method = "POST",
                bodyString = payload.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Échec sync menu du jour (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMenuOfDay(shopId: Long): Result<Set<Long>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptySet())
        try {
            val request = buildRequest("restaurant_menu_day?shop_id=eq.$shopId&select=*")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec lecture menu du jour (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() == 0) return@withContext Result.success(emptySet())
                val rawIds = arr.getJSONObject(0).optString("product_ids_json", "[]")
                val idArr = JSONArray(rawIds)
                val out = mutableSetOf<Long>()
                for (i in 0 until idArr.length()) {
                    val v = idArr.optLong(i, 0L)
                    if (v > 0L) out.add(v)
                }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            if (resolvedShopId > 1L) {
                val req = buildRequest("shop_profile?id=eq.$resolvedShopId&select=*&limit=1")
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        if (arr.length() > 0) {
                            resolvedShop = parseShopProfile(arr.getJSONObject(0))
                        }
                    }
                }
            }
            if (resolvedShop == null && fallbackShopName.isNotBlank()) {
                val req = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=500")
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val targetNorm = AgentDeploymentStorage.normalizeShopName(fallbackShopName)
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
                return@withContext Result.failure(IllegalStateException("Restaurant introuvable."))
            }

            val products = fetchProducts(resolvedShopId).getOrDefault(emptyList())
            val menuDayIds = fetchMenuOfDay(resolvedShopId).getOrDefault(emptySet())
            val tables = fetchRestaurantTables(resolvedShopId).getOrDefault(emptyList())
            val filtered = if (menuDayIds.isNotEmpty()) {
                val matched = products.filter { p ->
                    p.isAvailable && (
                        p.id in menuDayIds ||
                        MultiShopStorage.scopedCloudId(resolvedShopId, p.id) in menuDayIds ||
                        (p.id % 100000L) in menuDayIds
                    )
                }
                if (matched.isNotEmpty()) matched else products.filter { it.isAvailable }
            } else {
                products.filter { it.isAvailable }
            }

            Result.success(
                PublicRestaurantMenuBundle(
                    shopId = resolvedShopId,
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
            val request = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=300")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest("couture_models?is_available=eq.true&select=*&order=updated_at.desc&limit=300")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "couture_models?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(models.size)
                else Result.failure(IOException("Échec sync modèles couture (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCoutureModels(shopId: Long): Result<List<CoutureModelRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("couture_models?shop_id=eq.$shopId&select=*&order=updated_at.desc")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement modèles (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                Result.success((0 until arr.length()).map { CoutureModelRecord.fromJson(arr.getJSONObject(it)) })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCoutureModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || modelId.isBlank()) return@withContext Result.success(Unit)
        try {
            val enc = URLEncoder.encode(modelId, "UTF-8")
            val request = buildRequest("couture_models?id=eq.$enc", method = "DELETE")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "couture_orders?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(orders.size)
                else Result.failure(IOException("Échec sync fiches couture (${resp.code}): ${resp.body?.string() ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCoutureOrders(shopId: Long): Result<List<CoutureOrderRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("couture_orders?shop_id=eq.$shopId&select=*&order=updated_at.desc&limit=300")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec chargement fiches couture (${resp.code})"))
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                Result.success((0 until arr.length()).map { CoutureOrderRecord.fromJson(arr.getJSONObject(it)) })
            }
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
            val request = buildRequest("couture_orders?customer_phone=ilike.$enc&select=*&order=updated_at.desc&limit=100")
            client.newCall(request).execute().use { resp ->
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
            val request = buildRequest(
                endpoint = "couture_clients?on_conflict=id",
                method = "POST",
                bodyString = arr.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
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
                val req = buildRequest("shop_profile?id=eq.$resolvedShopId&select=*&limit=1")
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        if (arr.length() > 0) {
                            resolvedShop = parseShopProfile(arr.getJSONObject(0))
                        }
                    }
                }
            }
            if (resolvedShop == null && fallbackShopName.isNotBlank()) {
                val req = buildRequest("shop_profile?select=*&order=updated_at.desc&limit=500")
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val targetNorm = AgentDeploymentStorage.normalizeShopName(fallbackShopName)
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

            val models = fetchCoutureModels(resolvedShopId).getOrDefault(emptyList()).filter { it.isAvailable }
            val products = fetchProducts(resolvedShopId).getOrDefault(emptyList()).filter { it.isAvailable }

            Result.success(
                PublicCoutureCatalogBundle(
                    shopId = resolvedShopId,
                    shopName = resolvedShop?.name?.takeIf { it.isNotBlank() } ?: fallbackShopName.ifBlank { "Atelier Couture" },
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
}
