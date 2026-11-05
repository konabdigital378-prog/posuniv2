package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val supplierId: Long,
    val supplierName: String,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitCost: Long,
    val totalCost: Long,
    val isPaidFromCash: Boolean = true,
    val notes: String = ""
)
