package com.example.util

enum class PricingCategory(
    val id: String,
    val title: String,
    val badgeLabel: String,
    val description: String,
    val accentColorHex: Long
) {
    NOMAD_BROKER(
        id = "NOMAD_BROKER",
        title = "Tarif Mobilité & Courtage",
        badgeLabel = "TARIF RÉDUIT -70%",
        description = "Tarif social adapté aux vendeurs ambulants, marchands nomades et courtiers / démarcheurs.",
        accentColorHex = 0xFF0F766E
    ),
    ARTISAN_MICRO(
        id = "ARTISAN_MICRO",
        title = "Tarif Artisan & Proximité",
        badgeLabel = "TARIF ALLÉGÉ",
        description = "Forfait souple pour salons de coiffure, couture, kiosques, librairies et stations de lavage.",
        accentColorHex = 0xFF7C3AED
    ),
    BOUTIQUE_ENTREPRISE(
        id = "BOUTIQUE_ENTREPRISE",
        title = "Tarif Boutique & Entreprise",
        badgeLabel = "BOUTIQUE & PME",
        description = "Tarif complet pour boutiques, alimentations, restaurants, pharmacies, quincailleries et sociétés.",
        accentColorHex = 0xFF073B9A
    )
}

data class ActivityPricingProfile(
    val businessType: String,
    val category: PricingCategory,
    val starterLabel: String,
    val standardMonthly: Long,
    val proMonthly: Long,
    val proAnnual: Long,
    val entrepriseMonthly: Long,
    val premiumLifetime: Long
) {
    val isReducedTariff: Boolean
        get() = category == PricingCategory.NOMAD_BROKER
}

object SubscriptionPricingHelper {

    private val nomadBrokerTypes = setOf(
        "Vendeur ambulant",
        "Courtier"
    )

    private val artisanMicroTypes = setOf(
        "Kiosque",
        "Coiffure",
        "Couture",
        "Lavage Auto-Moto",
        "Librairie"
    )

    fun getCategory(businessType: String?): PricingCategory {
        val clean = businessType?.trim().orEmpty()
        return when {
            nomadBrokerTypes.any { it.equals(clean, ignoreCase = true) } ||
                clean.contains("ambulant", ignoreCase = true) ||
                clean.contains("courtier", ignoreCase = true) -> PricingCategory.NOMAD_BROKER
            artisanMicroTypes.any { it.equals(clean, ignoreCase = true) } -> PricingCategory.ARTISAN_MICRO
            else -> PricingCategory.BOUTIQUE_ENTREPRISE
        }
    }

    fun isReducedActivity(businessType: String?): Boolean {
        return getCategory(businessType) == PricingCategory.NOMAD_BROKER
    }

    fun getPricingProfile(businessType: String?): ActivityPricingProfile {
        val type = businessType?.takeIf { it.isNotBlank() } ?: "Boutique"
        return when (getCategory(type)) {
            PricingCategory.NOMAD_BROKER -> ActivityPricingProfile(
                businessType = type,
                category = PricingCategory.NOMAD_BROKER,
                starterLabel = "Gratuit",
                standardMonthly = 1500L,
                proMonthly = 2500L,
                proAnnual = 12000L,
                entrepriseMonthly = 7500L,
                premiumLifetime = 30000L
            )
            PricingCategory.ARTISAN_MICRO -> ActivityPricingProfile(
                businessType = type,
                category = PricingCategory.ARTISAN_MICRO,
                starterLabel = "Gratuit",
                standardMonthly = 3000L,
                proMonthly = 6000L,
                proAnnual = 25000L,
                entrepriseMonthly = 15000L,
                premiumLifetime = 55000L
            )
            PricingCategory.BOUTIQUE_ENTREPRISE -> ActivityPricingProfile(
                businessType = type,
                category = PricingCategory.BOUTIQUE_ENTREPRISE,
                starterLabel = "Gratuit",
                standardMonthly = 5000L,
                proMonthly = 10000L,
                proAnnual = 35000L,
                entrepriseMonthly = 25000L,
                premiumLifetime = 75000L
            )
        }
    }

    fun getPlanMonthlyPrice(businessType: String?, tier: String): Long {
        val profile = getPricingProfile(businessType)
        return when (tier.lowercase()) {
            "gratuit", "starter" -> 0L
            "standard" -> profile.standardMonthly
            "professionnel", "pro" -> profile.proMonthly
            "entreprise" -> profile.entrepriseMonthly
            "premium" -> profile.premiumLifetime
            else -> profile.standardMonthly
        }
    }

    fun getPlanAnnualPrice(businessType: String?, tier: String): Long {
        val profile = getPricingProfile(businessType)
        return when (tier.lowercase()) {
            "gratuit", "starter" -> 0L
            "standard" -> profile.standardMonthly * 10L
            "professionnel", "pro" -> profile.proAnnual
            "entreprise" -> profile.entrepriseMonthly * 10L
            "premium" -> profile.premiumLifetime
            else -> profile.proAnnual
        }
    }

    fun getPlanLifetimePrice(businessType: String?): Long {
        return getPricingProfile(businessType).premiumLifetime
    }

    fun getPriceForDuration(businessType: String?, tier: String, durationDays: Int): Long {
        val profile = getPricingProfile(businessType)
        val cleanTier = tier.trim().lowercase()
        if (cleanTier == "gratuit" || cleanTier == "starter") return 0L
        if (cleanTier == "premium" || durationDays >= 3650) return profile.premiumLifetime

        val monthlyBase = getPlanMonthlyPrice(businessType, cleanTier)
        return when (durationDays) {
            30 -> monthlyBase
            90 -> (monthlyBase * 2.75).toLong()
            180 -> (monthlyBase * 5.2).toLong()
            365 -> if (cleanTier == "professionnel" || cleanTier == "pro") profile.proAnnual else monthlyBase * 10L
            else -> ((monthlyBase * durationDays) / 30.0).toLong().coerceAtLeast(monthlyBase)
        }
    }

    fun formatCompactPrice(amount: Long, periodLabel: String = ""): String {
        if (amount <= 0L) return "Gratuit"
        val base = CurrencyFormatter.formatFCFA(amount)
        return if (periodLabel.isBlank()) base else "$base $periodLabel"
    }
}
