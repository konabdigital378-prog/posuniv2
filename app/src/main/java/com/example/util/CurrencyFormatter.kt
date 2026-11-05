package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.FRENCH).apply {
        groupingSeparator = ' '
    }
    private val decimalFormat = DecimalFormat("#,###", symbols)

    fun formatFCFA(amount: Long): String {
        return "${decimalFormat.format(amount)} FCFA"
    }

    fun formatNumber(amount: Long): String {
        return decimalFormat.format(amount)
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.FRENCH)
        return sdf.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
        return sdf.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.FRENCH)
        return sdf.format(Date(timestamp))
    }
}
