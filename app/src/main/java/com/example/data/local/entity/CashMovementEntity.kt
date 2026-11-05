package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_movements")
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // SALE, EXPENSE, CASH_IN, CASH_OUT, DEBT_PAYMENT, SUPPLIER_PAYMENT
    val amount: Long, // in FCFA (positive)
    val reason: String,
    val paymentMethod: String = "Espèces",
    val operatorName: String = "Gérant",
    val relatedId: Long? = null // Sale ID or Purchase ID if applicable
)
