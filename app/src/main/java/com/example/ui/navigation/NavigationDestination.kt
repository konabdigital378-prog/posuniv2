package com.example.ui.navigation

enum class KonabScreen(val title: String) {
    ONBOARDING("Bienvenue"),
    DASHBOARD("Accueil"),
    POS("Vente"),
    PAYMENT("Paiement"),
    RECEIPT("Reçu de vente"),
    STOCK("Stock & Produits"),
    CUSTOMERS("Clients"),
    SUPPLIERS("Fournisseurs"),
    CASH("Caisse & Dépenses"),
    REPORTS("Rapports"),
    TRANSACTIONS("Historique des transactions"),
    SETTINGS("Paramètres"),
    SUBSCRIPTION("Abonnement")
}

sealed class SetupWizardDestination(
    val route: String,
    val stepNumber: Int,
    val title: String
) {
    data object Welcome : SetupWizardDestination("setup/welcome", 1, "Bienvenue")
    data object BusinessInfo : SetupWizardDestination("setup/business_info", 2, "Commerce")
    data object ActivityType : SetupWizardDestination("setup/activity_type", 3, "Activité")
    data object Customization : SetupWizardDestination("setup/customization", 4, "Personnalisation")
    data object AdminAccount : SetupWizardDestination("setup/admin_account", 5, "Compte Admin")
    data object Ready : SetupWizardDestination("setup/ready", 6, "Récapitulatif")
    data object RegisterShop : SetupWizardDestination("setup/register_shop", 7, "Enregistrer ma boutique")

    companion object {
        val allSteps = listOf(Welcome, BusinessInfo, ActivityType, Customization, AdminAccount, Ready, RegisterShop)

        fun fromRoute(route: String?): SetupWizardDestination {
            return allSteps.find { it.route == route } ?: Welcome
        }
    }
}
