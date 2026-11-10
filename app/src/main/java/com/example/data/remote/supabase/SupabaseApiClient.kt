package com.example.data.remote.supabase

import com.example.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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

    // --- PRODUCTS ---

    suspend fun pushProducts(products: List<ProductEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || products.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            products.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
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
                array.put(obj)
            }

            val request = buildRequest(
                endpoint = "products",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf(
                    "Prefer" to "resolution=merge-duplicates"
                )
            )

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(products.size)
                } else {
                    Result.failure(IOException("Échec envoi produits (${response.code}): ${response.body?.string()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProducts(): Result<List<ProductEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("products?select=*")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec récupération produits: ${response.code}"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
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
                            imageUri = o.optString("image_uri", "")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CUSTOMERS ---

    suspend fun pushCustomers(customers: List<CustomerEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || customers.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            customers.forEach { c ->
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("debt_amount", c.debtAmount)
                    put("total_spent", c.totalSpent)
                    put("total_sales_count", c.totalSalesCount)
                    put("status", c.status)
                    put("created_at", c.createdAt)
                }
                array.put(obj)
            }

            val request = buildRequest(
                endpoint = "customers",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(customers.size)
                } else {
                    Result.failure(IOException("Échec envoi clients (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCustomers(): Result<List<CustomerEntity>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(emptyList())
        try {
            val request = buildRequest("customers?select=*")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Échec récupération clients: ${response.code}"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SALES & SALE ITEMS ---

    suspend fun pushSales(sales: List<SaleEntity>, items: List<SaleItemEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || sales.isEmpty()) return@withContext Result.success(0)
        try {
            // 1. Push sales
            val salesArray = JSONArray()
            sales.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
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
                salesArray.put(obj)
            }

            val salesReq = buildRequest(
                endpoint = "sales",
                method = "POST",
                bodyString = salesArray.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )

            val salesRes = client.newCall(salesReq).execute()
            salesRes.close()

            // 2. Push sale items
            if (items.isNotEmpty()) {
                val itemsArray = JSONArray()
                items.forEach { itm ->
                    val obj = JSONObject().apply {
                        put("id", itm.id)
                        put("sale_id", itm.saleId)
                        put("product_id", itm.productId)
                        put("product_name", itm.productName)
                        put("quantity", itm.quantity)
                        put("unit_price", itm.unitPrice)
                        put("total_price", itm.totalPrice)
                    }
                    itemsArray.put(obj)
                }

                val itemsReq = buildRequest(
                    endpoint = "sale_items",
                    method = "POST",
                    bodyString = itemsArray.toString(),
                    extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
                )
                val itemsRes = client.newCall(itemsReq).execute()
                itemsRes.close()
            }

            Result.success(sales.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SHOP PROFILE ---

    suspend fun pushShopProfile(profile: ShopProfileEntity, cloudShopId: Long = 0L): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext Result.success(Unit)
        try {
            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

            val targetId = if (cloudShopId > 1L) cloudShopId else if (profile.id > 1L) profile.id else System.currentTimeMillis()

            val array = JSONArray()
            // 1. Primary profile row (id = 1) - used by app Room database, queries, web admin, and direct database inspection
            val primaryObj = JSONObject().apply {
                put("id", 1L)
                put("name", profile.name.trim())
                put("business_type", profile.businessType)
                put("phone", profile.phone)
                put("address", profile.address)
                put("city", profile.city)
                put("currency", profile.currency)
                put("slogan", profile.slogan)
                put("receipt_footer", profile.receiptFooter)
                put("subscription_tier", profile.subscriptionTier)
                put("updated_at", nowIso)
            }
            array.put(primaryObj)

            // 2. Also save targetId if distinct from 1 for multi-tenant and multi-device persistence
            if (targetId != 1L) {
                val storeObj = JSONObject().apply {
                    put("id", targetId)
                    put("name", profile.name.trim())
                    put("business_type", profile.businessType)
                    put("phone", profile.phone)
                    put("address", profile.address)
                    put("city", profile.city)
                    put("currency", profile.currency)
                    put("slogan", profile.slogan)
                    put("receipt_footer", profile.receiptFooter)
                    put("subscription_tier", profile.subscriptionTier)
                    put("updated_at", nowIso)
                }
                array.put(storeObj)
            }

            val request = buildRequest(
                endpoint = "shop_profile?on_conflict=id",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf(
                    "Prefer" to "resolution=merge-duplicates"
                )
            )

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("KonabSupabase", "Boutique \"${profile.name}\" (ID 1 & $targetId) synchronisée sur Supabase (code: ${response.code})")
                    Result.success(Unit)
                } else {
                    val errBody = response.body?.string() ?: ""
                    android.util.Log.e("KonabSupabase", "Échec push boutique (${response.code}): $errBody")
                    Result.failure(IOException("Échec mise à jour boutique: ${response.code} $errBody"))
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
                val o = jsonArr.getJSONObject(0)
                val profile = ShopProfileEntity(
                    id = o.optInt("id", 1),
                    name = o.optString("name", "Konab POS"),
                    businessType = o.optString("business_type", "Boutique"),
                    phone = o.optString("phone", ""),
                    address = o.optString("address", ""),
                    city = o.optString("city", ""),
                    currency = o.optString("currency", "FCFA"),
                    slogan = o.optString("slogan", ""),
                    receiptFooter = o.optString("receipt_footer", ""),
                    subscriptionTier = o.optString("subscription_tier", "Premium"),
                    isConfigured = true
                )
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SUPPLIERS ---

    suspend fun pushSuppliers(suppliers: List<SupplierEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || suppliers.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            suppliers.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("phone", s.phone)
                    put("address", s.address)
                    put("debt_amount", s.debtAmount)
                    put("total_purchased", s.totalPurchased)
                    put("created_at", s.createdAt)
                }
                array.put(obj)
            }
            val request = buildRequest("suppliers", "POST", array.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(suppliers.size)
                else Result.failure(IOException("Échec envoi fournisseurs: ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PURCHASES ---

    suspend fun pushPurchases(purchases: List<PurchaseEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || purchases.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            purchases.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("invoice_number", p.invoiceNumber)
                    put("timestamp", p.timestamp)
                    put("supplier_id", p.supplierId)
                    put("supplier_name", p.supplierName)
                    put("product_id", p.productId)
                    put("product_name", p.productName)
                    put("quantity", p.quantity)
                    put("unit_cost", p.unitCost)
                    put("total_cost", p.totalCost)
                    put("is_paid_from_cash", p.isPaidFromCash)
                    put("notes", p.notes)
                }
                array.put(obj)
            }
            val request = buildRequest("purchases", "POST", array.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(purchases.size)
                else Result.failure(IOException("Échec envoi achats: ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CASH MOVEMENTS ---

    suspend fun pushCashMovements(movements: List<CashMovementEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || movements.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            movements.forEach { m ->
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("timestamp", m.timestamp)
                    put("type", m.type)
                    put("amount", m.amount)
                    put("reason", m.reason)
                    put("payment_method", m.paymentMethod)
                    put("operator_name", m.operatorName)
                    if (m.relatedId != null) put("related_id", m.relatedId)
                }
                array.put(obj)
            }
            val request = buildRequest("cash_movements", "POST", array.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(movements.size)
                else Result.failure(IOException("Échec envoi mouvements caisse: ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CASH SESSIONS ---

    suspend fun pushCashSessions(sessions: List<CashSessionEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || sessions.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            sessions.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
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
                array.put(obj)
            }
            val request = buildRequest("cash_sessions", "POST", array.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(sessions.size)
                else Result.failure(IOException("Échec envoi sessions: ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---

    suspend fun pushNotifications(notifications: List<NotificationEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || notifications.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            notifications.forEach { n ->
                val obj = JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("message", n.message)
                    put("type", n.type)
                    put("priority", n.priority)
                    put("timestamp", n.timestamp)
                    put("is_read", n.isRead)
                    if (n.actionRoute != null) put("action_route", n.actionRoute)
                    if (n.referenceId != null) put("reference_id", n.referenceId)
                }
                array.put(obj)
            }
            val request = buildRequest("app_notifications", "POST", array.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(notifications.size)
                else Result.failure(IOException("Échec envoi notifications: ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- AGENTS & DEPLOYMENTS ---

    suspend fun pushAgentDeployments(deployments: List<AgentDeploymentRecord>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || deployments.isEmpty()) return@withContext Result.success(0)
        try {
            val array = JSONArray()
            deployments.forEach { d ->
                val obj = JSONObject().apply {
                    put("id", d.id)
                    put("agent_id", d.agentId)
                    put("agent_name", d.agentName)
                    put("agent_phone", d.agentPhone)
                    put("shop_name", d.shopName)
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
                endpoint = "agent_deployments",
                method = "POST",
                bodyString = array.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(deployments.size)
                } else {
                    val err = resp.body?.string() ?: ""
                    Result.failure(IOException("Échec synchronisation déploiements (${resp.code}): $err"))
                }
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
                else Result.failure(IOException("Échec enregistrement agent (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
