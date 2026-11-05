package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val debtAmount: Long = 0L, // Unpaid credit in FCFA
    val totalSpent: Long = 0L, // Total purchases in FCFA
    val totalSalesCount: Int = 0,
    val status: String = "Nouveau", // Nouveau, Régulier, Client fidèle, Débiteur
    val createdAt: Long = System.currentTimeMillis()
)
