package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "STOCK_ALERT", "SALE_COMPLETED", "DEBT_ALERT", "CASH_ALERT", "SUPABASE_SYNC", "SYSTEM"
    val priority: String = "NORMAL", // "LOW", "NORMAL", "HIGH", "URGENT"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val actionRoute: String? = null, // "stock", "pos", "customers", "cash", "settings"
    val referenceId: Long? = null
)
