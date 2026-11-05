package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionNumber: String, // e.g. "20261030-0012"
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val customerName: String = "Client de passage",
    val subtotal: Long = 0L,
    val discountAmount: Long = 0L,
    val taxAmount: Long = 0L,
    val totalAmount: Long = 0L,
    val totalCostPrice: Long = 0L, // Sum of buy prices to calculate net profit
    val paymentMethod: String = "Espèces", // Espèces, Orange Money, Moov Money, Wave, Carte bancaire, Crédit
    val paymentStatus: String = "PAYÉ", // PAYÉ, CRÉDIT, PARTIEL
    val amountPaid: Long = 0L,
    val cashierName: String = "Gérant",
    val notes: String = "",
    val isCancelled: Boolean = false,
    val cancelledAt: Long? = null,
    val cancellationReason: String? = null,
    val cancelledBy: String? = null
)
