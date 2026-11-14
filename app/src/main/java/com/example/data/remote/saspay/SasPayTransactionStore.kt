package com.example.data.remote.saspay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SasPayTransactionRecord(
    val id: String,
    val paymentId: String,
    val reference: String,
    val purpose: String, // "REABONNEMENT" ou "ENCAISSEMENT_POS"
    val tier: String,
    val durationDays: Int,
    val amountFcfa: Long,
    val channel: String,
    val phone: String,
    val checkoutUrl: String? = null,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("paymentId", paymentId)
        put("reference", reference)
        put("purpose", purpose)
        put("tier", tier)
        put("durationDays", durationDays)
        put("amountFcfa", amountFcfa)
        put("channel", channel)
        put("phone", phone)
        put("checkoutUrl", checkoutUrl ?: "")
        put("status", status)
        put("createdAt", createdAt)
        put("verifiedAt", verifiedAt ?: 0L)
    }

    companion object {
        fun fromJson(obj: JSONObject): SasPayTransactionRecord {
            val verifiedRaw = obj.optLong("verifiedAt", 0L)
            return SasPayTransactionRecord(
                id = obj.optString("id"),
                paymentId = obj.optString("paymentId"),
                reference = obj.optString("reference"),
                purpose = obj.optString("purpose", "REABONNEMENT"),
                tier = obj.optString("tier", "Professionnel"),
                durationDays = obj.optInt("durationDays", 365),
                amountFcfa = obj.optLong("amountFcfa", 0L),
                channel = obj.optString("channel", "SasPay"),
                phone = obj.optString("phone", ""),
                checkoutUrl = obj.optString("checkoutUrl").takeIf { it.isNotBlank() },
                status = obj.optString("status", "PENDING"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                verifiedAt = if (verifiedRaw > 0L) verifiedRaw else null
            )
        }
    }
}

object SasPayTransactionStore {
    private const val PREFS = "konab_saspay_tx_prefs"
    private const val KEY_LIST = "saspay_transactions_json"

    fun saveTransaction(context: Context, record: SasPayTransactionRecord) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = getTransactions(context).toMutableList()
            val idx = list.indexOfFirst {
                it.id == record.id || (record.paymentId.isNotBlank() && it.paymentId == record.paymentId)
            }
            if (idx >= 0) {
                list[idx] = record
            } else {
                list.add(0, record)
            }
            val arr = JSONArray()
            list.take(50).forEach { arr.put(it.toJson()) }
            prefs.edit().putString(KEY_LIST, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun updateStatus(context: Context, paymentId: String, newStatus: String, isPaid: Boolean) {
        val current = getTransactions(context)
        val match = current.firstOrNull { it.paymentId == paymentId || it.reference == paymentId } ?: return
        saveTransaction(
            context,
            match.copy(
                status = newStatus,
                verifiedAt = if (isPaid) System.currentTimeMillis() else match.verifiedAt
            )
        )
    }

    fun getTransactions(context: Context): List<SasPayTransactionRecord> {
        val list = mutableListOf<SasPayTransactionRecord>()
        try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    list.add(SasPayTransactionRecord.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
