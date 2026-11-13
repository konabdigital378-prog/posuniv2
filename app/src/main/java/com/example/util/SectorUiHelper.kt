package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object SectorUiHelper {

    fun getIcon(type: String): ImageVector = when (type) {
        "Boutique" -> Icons.Default.ShoppingBag
        "Commerce général" -> Icons.Default.ShoppingCart
        "Restaurant" -> Icons.Default.Restaurant
        "Boulangerie" -> Icons.Default.BakeryDining
        "Kiosque" -> Icons.Default.Storefront
        "Vendeur ambulant" -> Icons.Default.DirectionsWalk
        "Pharmacie" -> Icons.Default.LocalPharmacy
        "Quincaillerie" -> Icons.Default.Build
        "Coiffure" -> Icons.Default.ContentCut
        "Couture" -> Icons.Default.Checkroom
        "Pressing" -> Icons.Default.LocalLaundryService
        "Maçonnerie" -> Icons.Default.Domain
        "Menuiserie" -> Icons.Default.Carpenter
        "Soudure" -> Icons.Default.Hardware
        "Plomberie" -> Icons.Default.Plumbing
        "Électricité" -> Icons.Default.ElectricBolt
        "Mécanique" -> Icons.Default.CarRepair
        "Téléphonie" -> Icons.Default.PhoneAndroid
        "Librairie" -> Icons.Default.MenuBook
        "Agriculture" -> Icons.Default.Agriculture
        "Transport" -> Icons.Default.LocalShipping
        "Autre" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Category
    }

    fun getAccentColor(type: String): Color {
        val hex = BusinessTypePresets.getPreset(type).accentColorHex
        return Color(hex)
    }

    fun getFeatureBadges(features: SectorFeatures): List<String> = buildList {
        if (features.hasTables) add("Tables & Cuisine")
        if (features.hasVariations) add("Tailles & Variantes")
        if (features.hasPrescriptionExpiry) add("Ordonnance & Posologie")
        if (features.hasFractionalUnits) add("Vente Vrac (m, kg, L)")
        if (features.hasOperatorStylist) add("Praticiens & Équipe")
        if (features.hasJobSiteQuotes) add("Devis & Chantiers")
        if (features.hasNomadMode) add("Tournée Nomade")
        if (features.hasMeasurements) add("Mesures & Dépôt/Retrait")
        if (features.hasRepairTracking) add("Réparation & SAV")
        if (features.hasBatchDailyProduction) add("Fournées & Commandes")
        if (features.hasLogisticsDelivery) add("Livraison & Trajets")
        if (features.hasSpeedCashShortcuts) add("Monnaie Express")
    }
}
