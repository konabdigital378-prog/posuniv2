package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {

    const val CHANNEL_STOCK = "konab_stock_channel"
    const val CHANNEL_SALES = "konab_sales_channel"
    const val CHANNEL_DEBT = "konab_debt_channel"
    const val CHANNEL_SYNC = "konab_sync_channel"
    const val CHANNEL_SYSTEM = "konab_system_channel"
    const val CHANNEL_RESTAURANT = "konab_restaurant_channel"

    private val notificationIdCounter = AtomicInteger(1000)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val stockChannel = NotificationChannel(
                CHANNEL_STOCK,
                "Alertes de Stock Faible",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications lorsque le stock d'un produit atteint le seuil d'alerte"
                enableVibration(true)
            }

            val salesChannel = NotificationChannel(
                CHANNEL_SALES,
                "Ventes & Encaissements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Confirmations de ventes et résumés journaliers"
            }

            val debtChannel = NotificationChannel(
                CHANNEL_DEBT,
                "Relances & Crédits Clients",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes de dettes impayées et rappels clients"
                enableVibration(true)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Synchronisation Supabase Cloud",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Statut des sauvegardes et synchronisations cloud"
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Alertes Système Konab",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Mises à jour et alertes importantes de la boutique"
            }

            val restaurantChannel = NotificationChannel(
                CHANNEL_RESTAURANT,
                "Commandes Cuisine & Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes temps réel pour la cuisine (QR tables) et notifications serveur (Prêt à servir)"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(stockChannel, salesChannel, debtChannel, syncChannel, systemChannel, restaurantChannel)
            )
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun sendStockAlert(context: Context, productName: String, currentStock: Int, minStock: Int) {
        if (!hasNotificationPermission(context)) return

        val title = "⚠️ Alerte Stock : $productName"
        val message = "Plus que $currentStock unité(s) restante(s) (Seuil minimal : $minStock). Pensez à réapprovisionner !"

        val builder = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendSaleSuccess(context: Context, transactionNumber: String, totalAmount: Long, currency: String = "FCFA") {
        if (!hasNotificationPermission(context)) return

        val title = "✅ Vente enregistrée ($transactionNumber)"
        val message = "Vente validée avec succès pour un montant de $totalAmount $currency."

        val builder = NotificationCompat.Builder(context, CHANNEL_SALES)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendSaleCancelled(context: Context, transactionNumber: String, totalAmount: Long, reason: String, currency: String = "FCFA") {
        if (!hasNotificationPermission(context)) return

        val title = "🚫 Vente Annulée ($transactionNumber)"
        val message = "La vente $transactionNumber de $totalAmount $currency a été annulée. Motif : $reason. Les stocks ont été réintégrés."

        val builder = NotificationCompat.Builder(context, CHANNEL_SALES)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendDebtAlert(context: Context, customerName: String, debtAmount: Long, currency: String = "FCFA") {
        if (!hasNotificationPermission(context)) return

        val title = "💳 Relance Crédit Client"
        val message = "Le client $customerName a une créance en cours de $debtAmount $currency."

        val builder = NotificationCompat.Builder(context, CHANNEL_DEBT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendCloudSyncSuccess(context: Context, message: String) {
        if (!hasNotificationPermission(context)) return

        val title = "☁️ Synchronisation Supabase Réussie"

        val builder = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendCustomNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = CHANNEL_SYSTEM
    ) {
        if (!hasNotificationPermission(context)) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendRestaurantReadyAlert(
        context: Context,
        orderNumber: String,
        tableName: String,
        customerName: String,
        itemsSummary: String
    ) {
        if (!hasNotificationPermission(context)) return

        val title = "🍽️ Prêt à servir : $tableName ($orderNumber)"
        val message = "Le serveur est notifié pour $customerName ($itemsSummary). À récupérer en cuisine !"

        val builder = NotificationCompat.Builder(context, CHANNEL_RESTAURANT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendRestaurantQrOrderAlert(
        context: Context,
        orderNumber: String,
        tableName: String,
        customerName: String,
        totalAmount: Long,
        currency: String = "FCFA"
    ) {
        if (!hasNotificationPermission(context)) return

        val title = "📲 Commande QR en Cuisine : $tableName ($orderNumber)"
        val message = "$customerName a commandé via le QR Code ($totalAmount $currency)."

        val builder = NotificationCompat.Builder(context, CHANNEL_RESTAURANT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(getAppPendingIntent(context))
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationIdCounter.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
