package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

/**
 * Utility for generating, validating, and managing Konab POS license keys.
 * Format: KNB-[TIER]-[DURATION]-[RANDOM]-[CHECKSUM]
 * Example: KNB-PRO-1A-7X49-8K2A
 */
object LicenseHelper {

    private const val SECRET_SALT = "KONAB_POS_BURKINA_2026_SECRET_KEY"
    private const val PREFS_NAME = "konab_licenses_prefs"
    private const val PREF_KEY_SAVED_KEYS = "saved_licenses_json"

    val AVAILABLE_TIERS = listOf("Standard", "Professionnel", "Entreprise", "Premium")

    val AVAILABLE_DURATIONS = listOf(
        DurationOption("1M", "1 Mois (30 jours)", 30),
        DurationOption("3M", "3 Mois (90 jours)", 90),
        DurationOption("6M", "6 Mois (180 jours)", 180),
        DurationOption("1A", "1 An (365 jours)", 365),
        DurationOption("VIE", "Licence Permanente (10 ans)", 3650)
    )

    data class DurationOption(
        val code: String,
        val label: String,
        val days: Int
    )

    data class LicenseKeyData(
        val key: String,
        val tier: String,
        val durationCode: String,
        val durationLabel: String,
        val durationDays: Int,
        val createdAt: Long = System.currentTimeMillis(),
        val beneficiary: String = "",
        val note: String = ""
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("key", key)
                put("tier", tier)
                put("durationCode", durationCode)
                put("durationLabel", durationLabel)
                put("durationDays", durationDays)
                put("createdAt", createdAt)
                put("beneficiary", beneficiary)
                put("note", note)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): LicenseKeyData {
                return LicenseKeyData(
                    key = json.optString("key"),
                    tier = json.optString("tier", "Professionnel"),
                    durationCode = json.optString("durationCode", "1A"),
                    durationLabel = json.optString("durationLabel", "1 An (365 jours)"),
                    durationDays = json.optInt("durationDays", 365),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    beneficiary = json.optString("beneficiary", ""),
                    note = json.optString("note", "")
                )
            }
        }
    }

    data class LicenseValidation(
        val isValid: Boolean,
        val tier: String,
        val durationDays: Int,
        val message: String
    )

    /**
     * Map tier name to 3-letter code
     */
    fun getTierCode(tier: String): String {
        return when (tier.lowercase(Locale.ROOT)) {
            "standard", "std" -> "STD"
            "professionnel", "pro" -> "PRO"
            "entreprise", "ent" -> "ENT"
            "premium", "vip" -> "VIP"
            else -> "PRO"
        }
    }

    /**
     * Map 3-letter code back to full tier name
     */
    fun getTierName(code: String): String {
        return when (code.uppercase(Locale.ROOT)) {
            "STD" -> "Standard"
            "PRO" -> "Professionnel"
            "ENT" -> "Entreprise"
            "VIP" -> "Premium"
            else -> "Professionnel"
        }
    }

    /**
     * Calculates 4-character cryptographic checksum for the payload
     */
    fun calculateChecksum(rawPayload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest((rawPayload + SECRET_SALT).toByteArray(Charsets.UTF_8))
        val hexChars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Crockford-style without confusing chars
        val b0 = hash[0].toInt() and 0xFF
        val b1 = hash[1].toInt() and 0xFF
        val b2 = hash[2].toInt() and 0xFF
        val b3 = hash[3].toInt() and 0xFF

        return buildString {
            append(hexChars[b0 % hexChars.length])
            append(hexChars[b1 % hexChars.length])
            append(hexChars[b2 % hexChars.length])
            append(hexChars[b3 % hexChars.length])
        }
    }

    /**
     * Generates a new unique valid license key
     */
    fun generateKey(
        tier: String,
        durationCode: String,
        beneficiary: String = "",
        note: String = ""
    ): LicenseKeyData {
        val tierCode = getTierCode(tier)
        val duration = AVAILABLE_DURATIONS.firstOrNull { it.code.equals(durationCode, ignoreCase = true) }
            ?: AVAILABLE_DURATIONS.first { it.code == "1A" }

        // Generate 4 random characters (A-Z, 0-9)
        val allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val randomSegment = (1..4)
            .map { allowedChars[Random.nextInt(allowedChars.length)] }
            .joinToString("")

        val basePayload = "KNB-$tierCode-${duration.code}-$randomSegment"
        val checksum = calculateChecksum(basePayload)
        val fullKey = "$basePayload-$checksum"

        return LicenseKeyData(
            key = fullKey,
            tier = getTierName(tierCode),
            durationCode = duration.code,
            durationLabel = duration.label,
            durationDays = duration.days,
            createdAt = System.currentTimeMillis(),
            beneficiary = beneficiary,
            note = note
        )
    }

    /**
     * Generates a batch of N keys
     */
    fun generateBatch(
        tier: String,
        durationCode: String,
        count: Int,
        beneficiaryPrefix: String = ""
    ): List<LicenseKeyData> {
        val safeCount = count.coerceIn(1, 50)
        return (1..safeCount).map { index ->
            val beneficiary = if (beneficiaryPrefix.isNotBlank()) "$beneficiaryPrefix #$index" else ""
            generateKey(tier, durationCode, beneficiary = beneficiary)
        }
    }

    /**
     * Validates any entered code string
     */
    fun validateKey(rawCode: String, defaultFallbackTier: String = "Professionnel"): LicenseValidation {
        val clean = rawCode.trim().uppercase(Locale.ROOT)
        if (clean.isBlank()) {
            return LicenseValidation(false, defaultFallbackTier, 0, "Clé de licence vide")
        }

        // 1. Check legacy master codes
        val legacyMasterCodes = mapOf(
            "KONAB-2026-PRO" to Pair("Professionnel", 365),
            "KONAB-VIP-65413799" to Pair("Premium", 365),
            "KONAB-ENTERPRISE" to Pair("Entreprise", 365),
            "65413799" to Pair("Premium", 365),
            "SUPER-ADMIN-KONAB" to Pair("Entreprise", 3650)
        )

        if (legacyMasterCodes.containsKey(clean)) {
            val (t, days) = legacyMasterCodes[clean]!!
            return LicenseValidation(true, t, days, "Clé Maître Konab validée avec succès")
        }

        // 2. Structured Key: KNB-[TIER]-[DUR]-[RANDOM]-[CHECKSUM]
        val parts = clean.split("-")
        if (parts.size == 5 && parts[0] == "KNB") {
            val tierCode = parts[1]
            val durCode = parts[2]
            val randomSeg = parts[3]
            val providedChecksum = parts[4]

            val basePayload = "KNB-$tierCode-$durCode-$randomSeg"
            val expectedChecksum = calculateChecksum(basePayload)

            if (providedChecksum.equals(expectedChecksum, ignoreCase = true)) {
                val tier = getTierName(tierCode)
                val durationOption = AVAILABLE_DURATIONS.firstOrNull { it.code.equals(durCode, ignoreCase = true) }
                val days = durationOption?.days ?: 365
                return LicenseValidation(
                    isValid = true,
                    tier = tier,
                    durationDays = days,
                    message = "Licence officielle validée : Formule $tier ($days jours)"
                )
            } else {
                return LicenseValidation(false, defaultFallbackTier, 0, "Clé de licence invalide ou corrompue (Checksum incorrect)")
            }
        }

        // 3. Fallback for custom promo codes >= 6 chars with valid prefix
        if (clean.startsWith("KONAB-") && clean.length >= 8) {
            val tier = if (clean.contains("ENT")) "Entreprise" else if (clean.contains("STD")) "Standard" else defaultFallbackTier
            return LicenseValidation(true, tier, 365, "Clé promotionnelle Konab validée")
        }

        // 4. Fallback for testing / backward compatibility
        if (clean.length >= 6) {
            return LicenseValidation(true, defaultFallbackTier, 365, "Clé temporaire validée")
        }

        return LicenseValidation(false, defaultFallbackTier, 0, "Format de clé de licence non reconnu")
    }

    /**
     * Persists a newly generated license key in device SharedPreferences
     */
    fun saveGeneratedLicense(context: Context, license: LicenseKeyData) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = getSavedLicenses(context).toMutableList()
            // Add at top, limit to 100
            currentList.add(0, license)
            val trimmed = currentList.take(100)
            val array = JSONArray()
            trimmed.forEach { array.put(it.toJson()) }
            prefs.edit().putString(PREF_KEY_SAVED_KEYS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Returns the list of locally generated license keys
     */
    fun getSavedLicenses(context: Context): List<LicenseKeyData> {
        val list = mutableListOf<LicenseKeyData>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(PREF_KEY_SAVED_KEYS, null)
            if (!raw.isNullOrBlank()) {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    list.add(LicenseKeyData.fromJson(array.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {}

        // If empty, preload with standard initial sample master keys for immediate use
        if (list.isEmpty()) {
            val sample1 = generateKey("Professionnel", "1A", beneficiary = "Dépôt 65413799 - Bobo", note = "Licence Annuelle Pro")
            val sample2 = generateKey("Entreprise", "VIE", beneficiary = "Direction Konab", note = "Licence Permanente")
            val sample3 = generateKey("Standard", "3M", beneficiary = "Client Démo Ouaga", note = "Pack 3 Mois")
            list.addAll(listOf(sample1, sample2, sample3))
        }

        return list
    }
}
