package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val debtAmount: Long = 0L,
    val totalPurchased: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
