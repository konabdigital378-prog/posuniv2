package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_profile")
data class ShopProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Boutique Kaba",
    val slogan: String = "Mode - Chaussures - Accessoires",
    val phone: String = "65413799",
    val address: String = "Bobo-Dioulasso, Burkina Faso",
    val city: String = "Bobo-Dioulasso",
    val currency: String = "FCFA",
    val businessType: String = "Boutique", // Boutique, Coiffure, Kiosque, Restaurant, Pharmacie, Quincaillerie, Commerce général, Autre
    val adminPin: String = "1234",
    val cashierPin: String = "0000",
    val subscriptionTier: String = "Premium", // Gratuit, Standard, Professionnel, Premium, Entreprise
    val subscriptionExpiresAt: Long = 0L,
    val isConfigured: Boolean = false,
    val taxRate: Double = 0.0,
    val receiptFooter: String = "Merci pour votre confiance ! À très bientôt !"
)
