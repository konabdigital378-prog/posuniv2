package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_sessions")
data class CashSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingBalance: Long = 0L,
    val closingExpectedBalance: Long = 0L,
    val closingActualBalance: Long = 0L,
    val difference: Long = 0L,
    val status: String = "OPEN", // OPEN, CLOSED
    val openedBy: String = "Gérant",
    val closedBy: String? = null,
    val closingNotes: String = ""
)
