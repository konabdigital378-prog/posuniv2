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
    val receiptFooter: String = "Merci pour votre confiance ! À très bientôt !",
    val logoUri: String? = null,

    // --- Personnalisation avancée des factures & tickets ---
    val invoiceHeaderStyle: String = "MODERNE", // MODERNE, CLASSIQUE, MINIMALISTE, CADRE
    val invoicePaperSize: String = "THERMAL_58", // THERMAL_58, THERMAL_80, A4_INVOICE
    val invoiceShowLogo: Boolean = true,
    val invoiceShowCashier: Boolean = true,
    val invoiceShowQrCode: Boolean = true,
    val invoiceQrType: String = "VERIFICATION", // VERIFICATION, WHATSAPP, CUSTOM
    val invoiceQrCustomText: String = "",
    val invoiceTaxNumber: String = "", // IFU / NIF / RCCM
    val invoiceLegalNotice: String = "Marchandises vendues ne sont ni reprises ni échangées",
    val invoicePaymentDetails: String = "", // ex: Orange Money: 65413799 | Moov: 70123456 | Wave
    val invoiceShowPaymentDetails: Boolean = true
)
