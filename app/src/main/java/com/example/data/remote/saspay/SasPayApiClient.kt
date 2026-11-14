package com.example.data.remote.saspay

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SasPayNetworkOption(
    val code: String,
    val name: String,
    val countryCode: String,
    val currency: String = "XOF"
)

data class SasPayCountryOption(
    val code: String,
    val name: String,
    val dialCode: String,
    val currency: String,
    val networks: List<SasPayNetworkOption>
)

data class SasPayPaymentResult(
    val paymentId: String,
    val reference: String,
    val status: String,
    val isPaid: Boolean,
    val checkoutUrl: String? = null,
    val message: String = "",
    val amountFcfa: Long = 0L,
    val rawBody: String = ""
)

/**
 * Client REST officiel SasPay (https://api.saspay.me/api/v1)
 * Gère les réabonnements Konab POS et les encaissements Mobile Money / Carte.
 * La clé secrète est injectée via BuildConfig.SASPAY_SECRET_KEY (Secrets AI Studio / .env).
 */
class SasPayApiClient {

    companion object {
        const val BASE_URL = "https://api.saspay.me/api/v1"
        private const val PLACEHOLDER_KEY = "SASPAY_SECRET_KEY_PLACEHOLDER"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val PAID_STATUSES = setOf(
            "SUCCESS", "SUCCEEDED", "COMPLETED", "PAID", "VALIDATED", "SETTLED"
        )

        fun defaultWestAfricaCountries(): List<SasPayCountryOption> = listOf(
            SasPayCountryOption(
                code = "BF",
                name = "Burkina Faso",
                dialCode = "+226",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("orange_bf", "Orange Money BF", "BF", "XOF"),
                    SasPayNetworkOption("moov_bf", "Moov Money BF", "BF", "XOF"),
                    SasPayNetworkOption("wave_bf", "Wave BF", "BF", "XOF")
                )
            ),
            SasPayCountryOption(
                code = "CI",
                name = "Côte d'Ivoire",
                dialCode = "+225",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("orange_ci", "Orange Money CI", "CI", "XOF"),
                    SasPayNetworkOption("mtn_ci", "MTN MoMo CI", "CI", "XOF"),
                    SasPayNetworkOption("moov_ci", "Moov Money CI", "CI", "XOF"),
                    SasPayNetworkOption("wave_ci", "Wave CI", "CI", "XOF")
                )
            ),
            SasPayCountryOption(
                code = "BJ",
                name = "Bénin",
                dialCode = "+229",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("mtn_bj", "MTN MoMo Bénin", "BJ", "XOF"),
                    SasPayNetworkOption("moov_bj", "Moov Money Bénin", "BJ", "XOF")
                )
            ),
            SasPayCountryOption(
                code = "SN",
                name = "Sénégal",
                dialCode = "+221",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("wave_sn", "Wave Sénégal", "SN", "XOF"),
                    SasPayNetworkOption("orange_sn", "Orange Money SN", "SN", "XOF"),
                    SasPayNetworkOption("free_sn", "Free Money SN", "SN", "XOF")
                )
            ),
            SasPayCountryOption(
                code = "TG",
                name = "Togo",
                dialCode = "+228",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("tmoney_tg", "Mixx by Yas / T-Money", "TG", "XOF"),
                    SasPayNetworkOption("flooz_tg", "Moov Flooz Togo", "TG", "XOF")
                )
            ),
            SasPayCountryOption(
                code = "ML",
                name = "Mali",
                dialCode = "+223",
                currency = "XOF",
                networks = listOf(
                    SasPayNetworkOption("orange_ml", "Orange Money Mali", "ML", "XOF"),
                    SasPayNetworkOption("moov_ml", "Moov Money Mali", "ML", "XOF")
                )
            )
        )

        fun formatPhoneWithCountryCode(rawPhone: String, dialCode: String): String {
            val cleaned = rawPhone.trim().replace(" ", "").replace("-", "")
            if (cleaned.startsWith("+")) return cleaned
            val normalizedDial = if (dialCode.startsWith("+")) dialCode else "+$dialCode"
            val digitsOnly = cleaned.filter { it.isDigit() }
            if (digitsOnly.isEmpty()) return normalizedDial
            val bareDial = normalizedDial.removePrefix("+")
            return if (digitsOnly.startsWith(bareDial) && digitsOnly.length > 10) {
                "+$digitsOnly"
            } else {
                "$normalizedDial$digitsOnly"
            }
        }

        fun isStatusPaid(status: String?): Boolean {
            val normalized = status?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return normalized in PAID_STATUSES
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    val secretKey: String
        get() = BuildConfig.SASPAY_SECRET_KEY
            .trim()
            .takeIf { it.isNotBlank() && it != PLACEHOLDER_KEY }
            .orEmpty()

    val isConfigured: Boolean
        get() = secretKey.isNotBlank()

    suspend fun fetchCountries(): Result<List<SasPayCountryOption>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/countries/")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.success(defaultWestAfricaCountries())
                }
                val parsed = parseCountriesJson(body)
                if (parsed.isEmpty()) {
                    Result.success(defaultWestAfricaCountries())
                } else {
                    Result.success(parsed)
                }
            }
        } catch (_: Exception) {
            Result.success(defaultWestAfricaCountries())
        }
    }

    /**
     * Envoie une demande Mobile Money directe (Softpay Push USSD / OTP).
     */
    suspend fun initiateSoftpay(
        amountFcfa: Long,
        countryCode: String,
        dialCode: String,
        networkCode: String,
        customerPhone: String,
        customerName: String,
        customerEmail: String,
        description: String
    ): Result<SasPayPaymentResult> = withContext(Dispatchers.IO) {
        val key = secretKey
        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Clé API SasPay (SASPAY_SECRET_KEY) absente. Ajoutez-la dans le panneau Secrets d'AI Studio.")
            )
        }
        if (amountFcfa <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Le montant doit être supérieur à 0 FCFA."))
        }

        try {
            val nameParts = customerName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val firstName = nameParts.firstOrNull() ?: "Client"
            val lastName = nameParts.drop(1).joinToString(" ").ifBlank { "KonabPOS" }
            val formattedPhone = formatPhoneWithCountryCode(customerPhone, dialCode)
            val safeEmail = customerEmail.trim().ifBlank { "merchant@konabpos.app" }

            val payload = JSONObject().apply {
                put("amount", "$amountFcfa.00")
                put("currency", "XOF")
                put("country", countryCode.uppercase(Locale.ROOT))
                put("network", networkCode)
                put("description", description.ifBlank { "Réabonnement Konab POS" })
                put("customer", JSONObject().apply {
                    put("email", safeEmail)
                    put("first_name", firstName)
                    put("last_name", lastName)
                    put("phone", formattedPhone)
                })
            }

            val req = Request.Builder()
                .url("$BASE_URL/payments/softpay/")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Idempotency-Key", UUID.randomUUID().toString())
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("SasPay (${resp.code}) : ${extractErrorMessage(raw, resp.message)}")
                    )
                }
                Result.success(parsePaymentResponse(raw, amountFcfa))
            }
        } catch (e: Exception) {
            Result.failure(RuntimeException("Erreur réseau SasPay : ${e.localizedMessage ?: "Connexion impossible"}"))
        }
    }

    /**
     * Crée une session de paiement hébergée (Mobile Money, Wave, Carte bancaire).
     */
    suspend fun createCheckoutSession(
        amountFcfa: Long,
        customerName: String,
        customerEmail: String,
        description: String,
        returnUrl: String = "https://konabpos.app/saspay/return"
    ): Result<SasPayPaymentResult> = withContext(Dispatchers.IO) {
        val key = secretKey
        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Clé API SasPay (SASPAY_SECRET_KEY) absente. Ajoutez-la dans le panneau Secrets d'AI Studio.")
            )
        }
        if (amountFcfa <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Le montant doit être supérieur à 0 FCFA."))
        }

        try {
            val payload = JSONObject().apply {
                put("amount", "$amountFcfa.00")
                put("currency", "XOF")
                put("description", description.ifBlank { "Réabonnement Konab POS" })
                put("customer_email", customerEmail.trim().ifBlank { "merchant@konabpos.app" })
                put("customer_name", customerName.trim().ifBlank { "Client Konab POS" })
                put("return_url", returnUrl)
            }

            val req = Request.Builder()
                .url("$BASE_URL/checkout-sessions/")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Idempotency-Key", UUID.randomUUID().toString())
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("SasPay (${resp.code}) : ${extractErrorMessage(raw, resp.message)}")
                    )
                }
                Result.success(parsePaymentResponse(raw, amountFcfa))
            }
        } catch (e: Exception) {
            Result.failure(RuntimeException("Erreur réseau SasPay : ${e.localizedMessage ?: "Connexion impossible"}"))
        }
    }

    /**
     * Vérifie le statut officiel d'un paiement ou d'une session côté serveur SasPay.
     */
    suspend fun verifyPayment(paymentOrSessionId: String): Result<SasPayPaymentResult> = withContext(Dispatchers.IO) {
        val key = secretKey
        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Clé API SasPay (SASPAY_SECRET_KEY) non configurée.")
            )
        }
        val cleanId = paymentOrSessionId.trim()
        if (cleanId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Identifiant de paiement SasPay vide."))
        }

        try {
            val verifyReq = Request.Builder()
                .url("$BASE_URL/payments/$cleanId/verify/")
                .get()
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(verifyReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@withContext Result.success(parsePaymentResponse(raw, 0L))
                }
            }

            // Fallback : vérification via /checkout-sessions/{id}/ si l'ID est une session checkout
            val sessionReq = Request.Builder()
                .url("$BASE_URL/checkout-sessions/$cleanId/")
                .get()
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(sessionReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("Vérification SasPay (${resp.code}) : ${extractErrorMessage(raw, resp.message)}")
                    )
                }
                Result.success(parsePaymentResponse(raw, 0L))
            }
        } catch (e: Exception) {
            Result.failure(RuntimeException("Impossible de vérifier le paiement : ${e.localizedMessage ?: "Erreur réseau"}"))
        }
    }

    internal fun parseCountriesJson(raw: String): List<SasPayCountryOption> {
        return try {
            val trimmed = raw.trim()
            val array: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("results")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("countries")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
            val list = mutableListOf<SasPayCountryOption>()
            for (i in 0 until array.length()) {
                val cObj = array.optJSONObject(i) ?: continue
                val cCode = cObj.optString("code").ifBlank { cObj.optString("iso_code") }.uppercase(Locale.ROOT)
                val cName = cObj.optString("name").ifBlank { cCode }
                val cDial = cObj.optString("dial_code").ifBlank {
                    when (cCode) {
                        "BF" -> "+226"
                        "CI" -> "+225"
                        "BJ" -> "+229"
                        "SN" -> "+221"
                        "TG" -> "+228"
                        "ML" -> "+223"
                        else -> "+"
                    }
                }
                val cCurrency = cObj.optString("currency").ifBlank { "XOF" }
                val netsArr = cObj.optJSONArray("networks") ?: cObj.optJSONArray("operators") ?: JSONArray()
                val networks = mutableListOf<SasPayNetworkOption>()
                for (j in 0 until netsArr.length()) {
                    val nObj = netsArr.optJSONObject(j) ?: continue
                    val nCode = nObj.optString("code").ifBlank { nObj.optString("id") }
                    val nName = nObj.optString("name").ifBlank { nCode }
                    if (nCode.isNotBlank()) {
                        networks.add(SasPayNetworkOption(code = nCode, name = nName, countryCode = cCode, currency = cCurrency))
                    }
                }
                if (cCode.isNotBlank() && networks.isNotEmpty()) {
                    list.add(SasPayCountryOption(cCode, cName, cDial, cCurrency, networks))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parsePaymentResponse(raw: String, fallbackAmount: Long): SasPayPaymentResult {
        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        val data = root.optJSONObject("data") ?: root.optJSONObject("payment") ?: root

        val paymentId = data.optString("payment_id")
            .ifBlank { data.optString("id") }
            .ifBlank { data.optString("uuid") }
            .ifBlank { root.optString("id") }
            .ifBlank { data.optString("reference") }

        val reference = data.optString("reference")
            .ifBlank { data.optString("transaction_ref") }
            .ifBlank { paymentId }

        val status = data.optString("status")
            .ifBlank { root.optString("status") }
            .ifBlank { "PENDING" }
            .uppercase(Locale.ROOT)

        val checkoutUrl = data.optString("checkout_url")
            .ifBlank { root.optString("checkout_url") }
            .ifBlank { data.optString("payment_url") }
            .takeIf { it.isNotBlank() && it != "null" }

        val message = data.optString("message")
            .ifBlank { root.optString("message") }
            .ifBlank { data.optString("detail") }

        val parsedAmount = data.optDouble("amount", -1.0).let {
            if (it > 0.0) it.toLong() else fallbackAmount
        }

        return SasPayPaymentResult(
            paymentId = paymentId,
            reference = reference,
            status = status,
            isPaid = isStatusPaid(status),
            checkoutUrl = checkoutUrl,
            message = message,
            amountFcfa = parsedAmount,
            rawBody = raw
        )
    }

    private fun extractErrorMessage(raw: String, fallback: String): String {
        return try {
            val obj = JSONObject(raw)
            obj.optString("detail")
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error") }
                .ifBlank { fallback }
        } catch (_: Exception) {
            raw.take(180).ifBlank { fallback }
        }
    }
}
