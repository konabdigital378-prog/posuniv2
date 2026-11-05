package com.example.util

import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierEntity

data class BusinessTypeInfo(
    val id: String,
    val title: String,
    val iconName: String, // e.g. "shopping_bag", "content_cut", "storefront", "restaurant", "local_pharmacy", "build", "category", "more_horiz"
    val description: String,
    val defaultCategories: List<String>,
    val defaultProducts: List<ProductEntity>
)

object BusinessTypePresets {

    fun getAllTypes(): List<String> = listOf(
        "Boutique",
        "Coiffure",
        "Kiosque",
        "Restaurant",
        "Pharmacie",
        "Quincaillerie",
        "Commerce général",
        "Autre"
    )

    fun getPreset(type: String): BusinessTypeInfo {
        return when (type) {
            "Boutique" -> BusinessTypeInfo(
                id = "Boutique",
                title = "Boutique",
                iconName = "shopping_bag",
                description = "Prêt-à-porter, mode, chaussures, sacs et accessoires",
                defaultCategories = listOf("Vêtements", "Chaussures", "Accessoires", "Sacs"),
                defaultProducts = listOf(
                    ProductEntity(name = "T-shirt homme", category = "Vêtements", buyPrice = 1200, sellPrice = 2000, stockQuantity = 25, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Pantalon jeans", category = "Vêtements", buyPrice = 2800, sellPrice = 4000, stockQuantity = 15, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Chaussures cuir", category = "Chaussures", buyPrice = 4200, sellPrice = 6000, stockQuantity = 8, minStockAlert = 10, unit = "paire", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Casquette stylée", category = "Accessoires", buyPrice = 1400, sellPrice = 2500, stockQuantity = 5, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Sac bandoulière", category = "Sacs", buyPrice = 3200, sellPrice = 5000, stockQuantity = 2, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Montre quartz", category = "Accessoires", buyPrice = 5000, sellPrice = 8000, stockQuantity = 12, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Ceinture cuir", category = "Accessoires", buyPrice = 1800, sellPrice = 3000, stockQuantity = 18, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Robe pagne Faso Danfani", category = "Vêtements", buyPrice = 7500, sellPrice = 12000, stockQuantity = 9, minStockAlert = 4, unit = "pièce", imageUri = "img_prod_mode")
                )
            )

            "Restaurant" -> BusinessTypeInfo(
                id = "Restaurant",
                title = "Restaurant",
                iconName = "restaurant",
                description = "Plats chauds, grillades, boissons locales et desserts",
                defaultCategories = listOf("Plats chauds", "Grillades", "Boissons locales", "Accompagnements"),
                defaultProducts = listOf(
                    ProductEntity(name = "Riz gras au boeuf", category = "Plats chauds", buyPrice = 700, sellPrice = 1500, stockQuantity = 40, minStockAlert = 10, unit = "plat", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Poulet bicyclette braisé", category = "Grillades", buyPrice = 2500, sellPrice = 4500, stockQuantity = 15, minStockAlert = 5, unit = "portion", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Alloco / Banane frite", category = "Accompagnements", buyPrice = 300, sellPrice = 600, stockQuantity = 30, minStockAlert = 8, unit = "portion", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Jus de Bissap frais 50cl", category = "Boissons locales", buyPrice = 200, sellPrice = 500, stockQuantity = 50, minStockAlert = 15, unit = "bouteille", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Jus de Gingembre / Gnamakoudji", category = "Boissons locales", buyPrice = 200, sellPrice = 500, stockQuantity = 35, minStockAlert = 10, unit = "bouteille", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Tô sauce gombo", category = "Plats chauds", buyPrice = 500, sellPrice = 1000, stockQuantity = 25, minStockAlert = 5, unit = "plat", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Brochettes de boeuf (Lot de 3)", category = "Grillades", buyPrice = 600, sellPrice = 1200, stockQuantity = 20, minStockAlert = 8, unit = "lot", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Eau minérale 1.5L", category = "Boissons locales", buyPrice = 300, sellPrice = 500, stockQuantity = 45, minStockAlert = 10, unit = "bouteille", imageUri = "img_prod_boisson")
                )
            )

            "Kiosque" -> BusinessTypeInfo(
                id = "Kiosque",
                title = "Kiosque",
                iconName = "storefront",
                description = "Petit-déjeuner, café Touba, omelettes, pain et boissons",
                defaultCategories = listOf("Petit-déjeuner", "Boissons chaudes", "Sandwichs", "Snacks"),
                defaultProducts = listOf(
                    ProductEntity(name = "Café Touba épicé", category = "Boissons chaudes", buyPrice = 50, sellPrice = 150, stockQuantity = 100, minStockAlert = 20, unit = "tasse", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Thé vert à la menthe", category = "Boissons chaudes", buyPrice = 40, sellPrice = 100, stockQuantity = 80, minStockAlert = 15, unit = "verre", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Omelette garnie 2 oeufs", category = "Petit-déjeuner", buyPrice = 250, sellPrice = 500, stockQuantity = 60, minStockAlert = 15, unit = "portion", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain Mayonnaise Sardine", category = "Sandwichs", buyPrice = 400, sellPrice = 700, stockQuantity = 30, minStockAlert = 10, unit = "pain", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain Spaghetti Viande", category = "Sandwichs", buyPrice = 500, sellPrice = 1000, stockQuantity = 25, minStockAlert = 8, unit = "pain", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Beignets sucrés (Lot de 5)", category = "Snacks", buyPrice = 100, sellPrice = 250, stockQuantity = 40, minStockAlert = 10, unit = "lot", imageUri = "img_prod_resto")
                )
            )

            "Coiffure" -> BusinessTypeInfo(
                id = "Coiffure",
                title = "Coiffure & Salon",
                iconName = "content_cut",
                description = "Tresses, coupes, soins capillaires et cosmétiques",
                defaultCategories = listOf("Prestations Femmes", "Coupes Hommes", "Soins & Shampoing", "Produits cosmétiques"),
                defaultProducts = listOf(
                    ProductEntity(name = "Coupe homme classique + barbe", category = "Coupes Hommes", buyPrice = 0, sellPrice = 1000, stockQuantity = 100, minStockAlert = 1, unit = "service", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Tresses nattes collées", category = "Prestations Femmes", buyPrice = 500, sellPrice = 3500, stockQuantity = 50, minStockAlert = 1, unit = "service", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Mèches X-pression", category = "Produits cosmétiques", buyPrice = 1200, sellPrice = 2000, stockQuantity = 18, minStockAlert = 5, unit = "paquet", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Shampoing + Brushing", category = "Soins & Shampoing", buyPrice = 400, sellPrice = 2000, stockQuantity = 50, minStockAlert = 2, unit = "service", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Gel fixation forte", category = "Produits cosmétiques", buyPrice = 900, sellPrice = 1800, stockQuantity = 14, minStockAlert = 4, unit = "pot", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Pommade karité pure 250g", category = "Produits cosmétiques", buyPrice = 600, sellPrice = 1200, stockQuantity = 22, minStockAlert = 5, unit = "pot", imageUri = "img_prod_epicerie")
                )
            )

            "Pharmacie" -> BusinessTypeInfo(
                id = "Pharmacie",
                title = "Pharmacie & Dépôt",
                iconName = "local_pharmacy",
                description = "Médicaments essentiels, premiers soins, vitamines et parapharmacie",
                defaultCategories = listOf("Antalgiques", "Premiers secours", "Vitamines", "Hygiène"),
                defaultProducts = listOf(
                    ProductEntity(name = "Paracétamol 500mg (Boîte)", category = "Antalgiques", buyPrice = 300, sellPrice = 600, stockQuantity = 45, minStockAlert = 15, unit = "boîte", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Vitamine C 500mg effervescente", category = "Vitamines", buyPrice = 900, sellPrice = 1600, stockQuantity = 18, minStockAlert = 5, unit = "tube", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Sérum physiologique 100ml", category = "Premiers secours", buyPrice = 450, sellPrice = 850, stockQuantity = 30, minStockAlert = 10, unit = "flacon", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Alcool chirurgical 70° 250ml", category = "Premiers secours", buyPrice = 700, sellPrice = 1300, stockQuantity = 12, minStockAlert = 5, unit = "flacon", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Compresses stériles 10x10", category = "Premiers secours", buyPrice = 250, sellPrice = 500, stockQuantity = 40, minStockAlert = 10, unit = "paquet", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Bétadine dermique", category = "Premiers secours", buyPrice = 1200, sellPrice = 2200, stockQuantity = 8, minStockAlert = 4, unit = "flacon", imageUri = "img_prod_epicerie")
                )
            )

            "Quincaillerie" -> BusinessTypeInfo(
                id = "Quincaillerie",
                title = "Quincaillerie",
                iconName = "build",
                description = "Outillage, ciment, plomberie, électricité et quincaillerie générale",
                defaultCategories = listOf("Outillage", "Électricité", "Plomberie", "Visserie & Fixation"),
                defaultProducts = listOf(
                    ProductEntity(name = "Sac de ciment CIMBURKINA 50kg", category = "Gros oeuvre", buyPrice = 6200, sellPrice = 7200, stockQuantity = 35, minStockAlert = 10, unit = "sac", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Ampoule LED 12W économique", category = "Électricité", buyPrice = 700, sellPrice = 1400, stockQuantity = 50, minStockAlert = 15, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Robinet chromé quart de tour", category = "Plomberie", buyPrice = 1800, sellPrice = 3200, stockQuantity = 14, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Marteau manche bois 500g", category = "Outillage", buyPrice = 2100, sellPrice = 3800, stockQuantity = 9, minStockAlert = 4, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Mètre ruban 5m professionnel", category = "Outillage", buyPrice = 1000, sellPrice = 2000, stockQuantity = 16, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Boîte de pointes 60mm (1kg)", category = "Visserie & Fixation", buyPrice = 800, sellPrice = 1400, stockQuantity = 28, minStockAlert = 8, unit = "kg", imageUri = "img_prod_quincaillerie")
                )
            )

            else -> BusinessTypeInfo(
                id = "Commerce général",
                title = "Commerce général",
                iconName = "category",
                description = "Alimentation générale, épicerie, produits de première nécessité",
                defaultCategories = listOf("Alimentation", "Boissons", "Entretien & Savons", "Divers"),
                defaultProducts = listOf(
                    ProductEntity(name = "Riz blanc parfumé 5kg", category = "Alimentation", buyPrice = 2400, sellPrice = 3500, stockQuantity = 20, minStockAlert = 5, unit = "sac", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Huile végétale raffinée 1L", category = "Alimentation", buyPrice = 900, sellPrice = 1300, stockQuantity = 30, minStockAlert = 8, unit = "bouteille", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Savon de Marseille SN-CITEC", category = "Entretien & Savons", buyPrice = 350, sellPrice = 500, stockQuantity = 45, minStockAlert = 10, unit = "morceau", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Sucre blanc en morceaux 1kg", category = "Alimentation", buyPrice = 650, sellPrice = 900, stockQuantity = 25, minStockAlert = 8, unit = "paquet", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Lait concentré sucré 397g", category = "Alimentation", buyPrice = 600, sellPrice = 950, stockQuantity = 22, minStockAlert = 6, unit = "boîte", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Pâtes alimentaires 500g", category = "Alimentation", buyPrice = 350, sellPrice = 550, stockQuantity = 40, minStockAlert = 10, unit = "paquet", imageUri = "img_prod_epicerie")
                )
            )
        }
    }

    fun getDefaultCustomers(): List<CustomerEntity> = listOf(
        CustomerEntity(name = "Awa Diallo", phone = "+226 70 12 34 56", address = "Secteur 15", debtAmount = 0, totalSpent = 145000, totalSalesCount = 14, status = "Client fidèle"),
        CustomerEntity(name = "Moussa Traoré", phone = "+226 76 98 76 54", address = "Colma", debtAmount = 8500, totalSpent = 88000, totalSalesCount = 9, status = "Régulier"),
        CustomerEntity(name = "Fatou Sanou", phone = "+226 71 23 45 67", address = "Accart-Ville", debtAmount = 0, totalSpent = 62000, totalSalesCount = 6, status = "Régulier"),
        CustomerEntity(name = "Issa Ouédraogo", phone = "+226 78 56 78 90", address = "Koko", debtAmount = 12000, totalSpent = 35000, totalSalesCount = 3, status = "Nouveau"),
        CustomerEntity(name = "Sami Konaté", phone = "+226 75 43 21 09", address = "Bindougousso", debtAmount = 0, totalSpent = 54000, totalSalesCount = 5, status = "Régulier")
    )

    fun getDefaultSuppliers(): List<SupplierEntity> = listOf(
        SupplierEntity(name = "Grossiste Central Faso", phone = "+226 20 97 11 22", address = "Grand Marché Bobo", debtAmount = 25000, totalPurchased = 450000),
        SupplierEntity(name = "Import-Export Sahel", phone = "+226 25 30 40 50", address = "Zone Industrielle Ouaga", debtAmount = 0, totalPurchased = 320000),
        SupplierEntity(name = "Distributeur Bobo Frais", phone = "+226 70 44 55 66", address = "Secteur 22", debtAmount = 15000, totalPurchased = 180000)
    )
}
