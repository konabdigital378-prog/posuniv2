package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
        "Vendeur ambulant" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "Courtier" -> Icons.Default.Handshake
        "Pharmacie" -> Icons.Default.LocalPharmacy
        "Quincaillerie" -> Icons.Default.Build
        "Coiffure" -> Icons.Default.ContentCut
        "Couture" -> Icons.Default.Checkroom
        "Pressing" -> Icons.Default.LocalLaundryService
        "Boucherie" -> Icons.Default.Restaurant
        "Poissonnerie" -> Icons.Default.SetMeal
        "Agro-pastoral", "Agriculture" -> Icons.Default.Agriculture
        "Informatique", "Téléphonie" -> Icons.Default.Smartphone
        "Librairie" -> Icons.AutoMirrored.Filled.MenuBook
        "Bijouterie" -> Icons.Default.Diamond
        "Lavage Auto-Moto" -> Icons.Default.LocalCarWash
        "Maçonnerie" -> Icons.Default.Domain
        "Menuiserie" -> Icons.Default.Carpenter
        "Soudure" -> Icons.Default.Hardware
        "Plomberie" -> Icons.Default.Plumbing
        "Électricité" -> Icons.Default.ElectricBolt
        "Mécanique" -> Icons.Default.CarRepair
        "Transport" -> Icons.Default.LocalShipping
        "Autre" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Category
    }

    fun getSectorGroups(): List<String> = listOf(
        "Tous",
        "Commerce",
        "Alimentation",
        "Artisanat & BTP",
        "Services & Tech"
    )

    fun matchesGroup(type: String, group: String): Boolean {
        return when (group) {
            "Commerce" -> type in listOf("Boutique", "Commerce général", "Kiosque", "Vendeur ambulant", "Courtier", "Bijouterie", "Librairie", "Autre")
            "Alimentation" -> type in listOf("Restaurant", "Boulangerie", "Boucherie", "Poissonnerie", "Commerce général", "Kiosque")
            "Artisanat & BTP" -> type in listOf("Maçonnerie", "Menuiserie", "Soudure", "Plomberie", "Électricité", "Quincaillerie", "Agro-pastoral")
            "Services & Tech" -> type in listOf("Coiffure", "Couture", "Pressing", "Courtier", "Informatique", "Lavage Auto-Moto", "Transport", "Mécanique", "Pharmacie", "Autre")
            else -> true
        }
    }

    fun getAccentColor(type: String): Color {
        val hex = BusinessTypePresets.getPreset(type).accentColorHex
        return Color(hex)
    }

    fun getFeatureBadges(features: SectorFeatures): List<String> = buildList {
        if (features.hasTables) add("Tables & Cuisine")
        if (features.hasVariations) add("Tailles & Variantes")
        if (features.hasPrescriptionExpiry) add("Ordonnance & Posologie")
        if (features.hasFractionalUnits) add("Unités & Vrac (m, kg, L)")
        if (features.hasOperatorStylist) add("Praticiens & Équipe")
        if (features.hasJobSiteQuotes) add("Devis & Chantiers")
        if (features.hasNomadMode) add("Mobilité & Express")
        if (features.hasSpeedCashShortcuts) add("Monnaie Express")
    }

    fun getUnitBadgeLabel(unit: String): String = when (unit.lowercase()) {
        "kg" -> "au kilo (kg)"
        "demi-kg" -> "au 1/2 kg"
        "gramme" -> "au gramme (g)"
        "mètre", "m" -> "au mètre (m)"
        "m²" -> "au m²"
        "m³" -> "au m³"
        "litre" -> "au litre (L)"
        "commission" -> "par commission"
        "dossier" -> "par dossier"
        "course" -> "la course"
        "colis" -> "par colis"
        "service", "prestation" -> "la prestation"
        "lavage" -> "le lavage"
        "tenue", "confection" -> "la tenue"
        "retouche" -> "la retouche"
        "voyage" -> "le voyage"
        else -> "/ $unit"
    }
}
