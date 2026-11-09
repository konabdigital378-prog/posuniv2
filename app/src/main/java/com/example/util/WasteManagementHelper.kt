package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WasteConfig(
    val collectorName: String = "GIE Salubrité & Propreté",
    val collectorPhone: String = "65413799",
    val monthlyFee: Long = 2500L,
    val pickupDays: List<String> = listOf("Mardi", "Vendredi"),
    val pickupTime: String = "07h30 - 09h30",
    val lastPaymentDate: Long = System.currentTimeMillis() - 10 * 86400000L,
    val lastPaymentPeriod: String = "Novembre 2026",
    val notes: String = "Sortir les bacs et sacs fermés devant la boutique avant 08h00.",
    val isServiceActive: Boolean = true
)

data class WastePaymentRecord(
    val id: String = "POUB-${System.currentTimeMillis() % 100000}",
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Long = 2500L,
    val period: String = "Novembre 2026",
    val paymentMethod: String = "Espèces",
    val collectorName: String = "GIE Salubrité & Propreté",
    val notes: String = "Cotisation mensuelle ramassage ordures"
)

object WasteManagementHelper {
    private const val PREF_NAME = "konab_waste_management_prefs"
    private const val KEY_CONFIG = "waste_config_json"
    private const val KEY_PAYMENTS = "waste_payments_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getConfig(context: Context): WasteConfig {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_CONFIG, null) ?: return WasteConfig()
        return try {
            val json = JSONObject(jsonStr)
            val daysArray = json.optJSONArray("pickupDays") ?: JSONArray().apply {
                put("Mardi")
                put("Vendredi")
            }
            val daysList = mutableListOf<String>()
            for (i in 0 until daysArray.length()) {
                daysList.add(daysArray.getString(i))
            }
            WasteConfig(
                collectorName = json.optString("collectorName", "GIE Salubrité & Propreté"),
                collectorPhone = json.optString("collectorPhone", "65413799"),
                monthlyFee = json.optLong("monthlyFee", 2500L),
                pickupDays = if (daysList.isNotEmpty()) daysList else listOf("Mardi", "Vendredi"),
                pickupTime = json.optString("pickupTime", "07h30 - 09h30"),
                lastPaymentDate = json.optLong("lastPaymentDate", System.currentTimeMillis()),
                lastPaymentPeriod = json.optString("lastPaymentPeriod", "Novembre 2026"),
                notes = json.optString("notes", "Sortir les bacs et sacs fermés avant 08h00."),
                isServiceActive = json.optBoolean("isServiceActive", true)
            )
        } catch (_: Exception) {
            WasteConfig()
        }
    }

    fun saveConfig(context: Context, config: WasteConfig) {
        val prefs = getPrefs(context)
        val json = JSONObject().apply {
            put("collectorName", config.collectorName)
            put("collectorPhone", config.collectorPhone)
            put("monthlyFee", config.monthlyFee)
            val arr = JSONArray()
            config.pickupDays.forEach { arr.put(it) }
            put("pickupDays", arr)
            put("pickupTime", config.pickupTime)
            put("lastPaymentDate", config.lastPaymentDate)
            put("lastPaymentPeriod", config.lastPaymentPeriod)
            put("notes", config.notes)
            put("isServiceActive", config.isServiceActive)
        }
        prefs.edit().putString(KEY_CONFIG, json.toString()).apply()
    }

    fun getPayments(context: Context): List<WastePaymentRecord> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_PAYMENTS, null)
        if (jsonStr.isNullOrBlank()) {
            // Seed a realistic initial payment
            val defaultList = listOf(
                WastePaymentRecord(
                    id = "POUB-9421",
                    timestamp = System.currentTimeMillis() - 8 * 86400000L,
                    amount = 2500L,
                    period = "Octobre 2026",
                    paymentMethod = "Espèces",
                    collectorName = "GIE Salubrité & Propreté",
                    notes = "Règlement cotisation mensuelle éboueurs"
                )
            )
            savePayments(context, defaultList)
            return defaultList
        }

        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<WastePaymentRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WastePaymentRecord(
                        id = obj.optString("id", "POUB-001"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        amount = obj.optLong("amount", 2500L),
                        period = obj.optString("period", "Novembre 2026"),
                        paymentMethod = obj.optString("paymentMethod", "Espèces"),
                        collectorName = obj.optString("collectorName", "GIE Salubrité"),
                        notes = obj.optString("notes", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePayments(context: Context, payments: List<WastePaymentRecord>) {
        val prefs = getPrefs(context)
        val arr = JSONArray()
        payments.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("timestamp", p.timestamp)
                put("amount", p.amount)
                put("period", p.period)
                put("paymentMethod", p.paymentMethod)
                put("collectorName", p.collectorName)
                put("notes", p.notes)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PAYMENTS, arr.toString()).apply()
    }

    fun addPayment(context: Context, payment: WastePaymentRecord) {
        val existing = getPayments(context).toMutableList()
        existing.add(0, payment)
        savePayments(context, existing)

        // Also update lastPaymentDate and lastPaymentPeriod in config
        val currentCfg = getConfig(context)
        saveConfig(
            context,
            currentCfg.copy(
                lastPaymentDate = payment.timestamp,
                lastPaymentPeriod = payment.period
            )
        )
    }

    /**
     * Calculates textual hint for next pickup:
     * e.g., "Aujourd'hui (07h30 - 09h30)", "Demain matin (Mardi)", "Vendredi prochain"
     */
    fun getNextPickupHint(config: WasteConfig): String {
        if (!config.isServiceActive || config.pickupDays.isEmpty()) {
            return "Service suspendu"
        }

        val dayNames = listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")
        val cal = Calendar.getInstance()
        val currentDayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Dimanche, 1 = Lundi...
        val currentDayName = dayNames[currentDayIndex]

        if (config.pickupDays.any { it.equals(currentDayName, ignoreCase = true) }) {
            return "Aujourd'hui (${config.pickupTime}) • Sortir la poubelle !"
        }

        // Find nearest next day
        for (offset in 1..7) {
            val nextDayIndex = (currentDayIndex + offset) % 7
            val nextDayName = dayNames[nextDayIndex]
            if (config.pickupDays.any { it.equals(nextDayName, ignoreCase = true) }) {
                return if (offset == 1) {
                    "Demain ($nextDayName - ${config.pickupTime})"
                } else {
                    "$nextDayName (${config.pickupTime})"
                }
            }
        }

        return "${config.pickupDays.first()} (${config.pickupTime})"
    }

    fun isPaymentUpToDate(config: WasteConfig): Boolean {
        val oneMonthAgo = System.currentTimeMillis() - (32L * 86400000L)
        return config.lastPaymentDate >= oneMonthAgo
    }

    fun generateWastePaymentReceiptText(shopName: String, payment: WastePaymentRecord): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val dateStr = sdf.format(Date(payment.timestamp))
        return """
            ================================
                 REÇU DE SALUBRITÉ BOUTIQUE
            ================================
            Boutique : $shopName
            Reçu N° : ${payment.id}
            Date : $dateStr
            Prestataire : ${payment.collectorName}
            --------------------------------
            Objet : Ramassage & Salubrité
            Période : ${payment.period}
            Montant : ${CurrencyFormatter.formatFCFA(payment.amount)}
            Règlement : ${payment.paymentMethod}
            Statut : ACQUITTÉ / PAYÉ
            --------------------------------
            Merci de maintenir notre quartier propre !
            Application : Konab POS
            ================================
        """.trimIndent()
    }
}
