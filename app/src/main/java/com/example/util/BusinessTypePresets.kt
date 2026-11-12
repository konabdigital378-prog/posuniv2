package com.example.util

import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierEntity

data class SectorFeatures(
    val hasTables: Boolean = false,              // Restaurant, Bar, Maquis
    val hasVariations: Boolean = false,          // Mode, Prêt-à-porter, Menuiserie (tailles, couleurs, essences)
    val hasPrescriptionExpiry: Boolean = false,  // Pharmacie, Santé (lot, péremption, ordonnance)
    val hasFractionalUnits: Boolean = false,     // Quincaillerie, BTP, Métallurgie, Plomberie (kg, mètre, litre)
    val hasSpeedCashShortcuts: Boolean = false,  // Kiosque, Fast-food, Commerce rapide
    val hasOperatorStylist: Boolean = false,     // Coiffure, Salon, Garage Mécanique (ouvrier/mécanicien)
    val hasJobSiteQuotes: Boolean = false,       // PME & Artisans : Devis Chantier, Bon d'intervention proforma
    val hasNomadMode: Boolean = false            // Vendeur Ambulant & Commerce nomade : encaissement flash, tournée de rue
)

data class BusinessTypeInfo(
    val id: String,
    val title: String,
    val iconName: String,
    val description: String,
    val defaultCategories: List<String>,
    val defaultProducts: List<ProductEntity>,
    val quickUnits: List<String> = listOf("pièce", "lot", "paquet"),
    val sectorFeatures: SectorFeatures = SectorFeatures(),
    val emoji: String = "🏬"
) {
    val label: String get() = title
}

object BusinessTypePresets {

    fun getAllTypes(): List<String> = listOf(
        "Boutique",
        "Restaurant",
        "Kiosque",
        "Vendeur ambulant",
        "Pharmacie",
        "Quincaillerie",
        "Coiffure",
        "Commerce général",
        "Boulangerie",
        "Couture",
        "Pressing",
        "Boucherie",
        "Poissonnerie",
        "Agro-pastoral",
        "Informatique",
        "Librairie",
        "Bijouterie",
        "Lavage Auto-Moto",
        "Maçonnerie",
        "Menuiserie",
        "Soudure",
        "Plomberie",
        "Électricité",
        "Mécanique",
        "Autre"
    )

    fun getPreset(type: String): BusinessTypeInfo {
        return when (type) {
            "Boutique" -> BusinessTypeInfo(
                id = "Boutique",
                title = "Boutique & Mode",
                iconName = "shopping_bag",
                description = "Prêt-à-porter, mode, chaussures, sacs, tissus et accessoires",
                defaultCategories = listOf("Vêtements", "Chaussures", "Accessoires", "Sacs"),
                quickUnits = listOf("pièce", "paire", "lot", "ensemble", "mètre", "paquet"),
                sectorFeatures = SectorFeatures(hasVariations = true),
                emoji = "👗",
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
                title = "Restaurant, Maquis & Bar",
                iconName = "restaurant",
                description = "Plats chauds, grillades, boissons fraîches locales et desserts",
                defaultCategories = listOf("Plats chauds", "Grillades", "Boissons locales", "Accompagnements"),
                quickUnits = listOf("plat", "portion", "bouteille", "verre", "tasse", "lot"),
                sectorFeatures = SectorFeatures(hasTables = true, hasSpeedCashShortcuts = true),
                emoji = "🍽️",
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
                title = "Kiosque & Fast-Food",
                iconName = "storefront",
                description = "Petit-déjeuner, café Touba, omelettes, pain garni et boissons",
                defaultCategories = listOf("Petit-déjeuner", "Boissons chaudes", "Sandwichs", "Snacks"),
                quickUnits = listOf("tasse", "verre", "pain", "portion", "lot", "bouteille"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true, hasTables = true),
                emoji = "🏪",
                defaultProducts = listOf(
                    ProductEntity(name = "Café Touba épicé", category = "Boissons chaudes", buyPrice = 50, sellPrice = 150, stockQuantity = 100, minStockAlert = 20, unit = "tasse", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Thé vert à la menthe", category = "Boissons chaudes", buyPrice = 40, sellPrice = 100, stockQuantity = 80, minStockAlert = 15, unit = "verre", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Omelette garnie 2 oeufs", category = "Petit-déjeuner", buyPrice = 250, sellPrice = 500, stockQuantity = 60, minStockAlert = 15, unit = "portion", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain Mayonnaise Sardine", category = "Sandwichs", buyPrice = 400, sellPrice = 700, stockQuantity = 30, minStockAlert = 10, unit = "pain", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain Spaghetti Viande", category = "Sandwichs", buyPrice = 500, sellPrice = 1000, stockQuantity = 25, minStockAlert = 8, unit = "pain", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Beignets sucrés (Lot de 5)", category = "Snacks", buyPrice = 100, sellPrice = 250, stockQuantity = 40, minStockAlert = 10, unit = "lot", imageUri = "img_prod_resto")
                )
            )

            "Vendeur ambulant" -> BusinessTypeInfo(
                id = "Vendeur ambulant",
                title = "Vendeur Ambulant & Commerce Nomade",
                iconName = "directions_walk",
                description = "Vente nomade, carrefours, tabliers, marchés de rue, tournées mobiles",
                defaultCategories = listOf("Boissons Fraîches", "Snacks & Friandises", "Hygiène & Pratique", "Divers"),
                quickUnits = listOf("pièce", "sachet", "tas", "paquet", "bouteille"),
                sectorFeatures = SectorFeatures(
                    hasNomadMode = true,
                    hasSpeedCashShortcuts = true
                ),
                emoji = "🚶",
                defaultProducts = listOf(
                    ProductEntity(name = "Sachet d'eau fraîche (Lot 10)", category = "Boissons Fraîches", buyPrice = 200, sellPrice = 500, stockQuantity = 60, minStockAlert = 10, unit = "sachet", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Bouteille eau minérale fraîche 500ml", category = "Boissons Fraîches", buyPrice = 150, sellPrice = 250, stockQuantity = 40, minStockAlert = 8, unit = "bouteille", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Boisson gazeuse canette 33cl", category = "Boissons Fraîches", buyPrice = 350, sellPrice = 500, stockQuantity = 30, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_boisson"),
                    ProductEntity(name = "Arachides grillées (Sachet)", category = "Snacks & Friandises", buyPrice = 60, sellPrice = 100, stockQuantity = 50, minStockAlert = 10, unit = "sachet", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Biscuits gourmands (Paquet)", category = "Snacks & Friandises", buyPrice = 125, sellPrice = 200, stockQuantity = 35, minStockAlert = 6, unit = "paquet", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Bonbons menthe fraîcheur (Tas de 5)", category = "Snacks & Friandises", buyPrice = 50, sellPrice = 100, stockQuantity = 80, minStockAlert = 15, unit = "tas", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Paquet mouchoirs de poche", category = "Hygiène & Pratique", buyPrice = 50, sellPrice = 100, stockQuantity = 50, minStockAlert = 10, unit = "paquet", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Carte recharge téléphonique 1000F", category = "Divers", buyPrice = 950, sellPrice = 1000, stockQuantity = 20, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_epicerie")
                )
            )

            "Coiffure" -> BusinessTypeInfo(
                id = "Coiffure",
                title = "Coiffure & Salon de Beauté",
                iconName = "content_cut",
                description = "Tresses, coupes, soins capillaires, esthétique et cosmétiques",
                defaultCategories = listOf("Prestations Femmes", "Coupes Hommes", "Soins & Shampoing", "Produits cosmétiques"),
                quickUnits = listOf("service", "prestation", "pot", "flacon", "paquet", "tube"),
                sectorFeatures = SectorFeatures(hasOperatorStylist = true),
                emoji = "💇",
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
                title = "Pharmacie & Dépôt Pharmaceutique",
                iconName = "local_pharmacy",
                description = "Médicaments essentiels, premiers soins, vitamines et parapharmacie",
                defaultCategories = listOf("Antalgiques", "Premiers secours", "Vitamines", "Hygiène"),
                quickUnits = listOf("boîte", "plaquette", "flacon", "tube", "sachet", "ampoule", "pièce"),
                sectorFeatures = SectorFeatures(hasPrescriptionExpiry = true),
                emoji = "💊",
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
                title = "Quincaillerie & Matériaux",
                iconName = "build",
                description = "Outillage, ciment, plomberie, électricité, visserie et matériaux",
                defaultCategories = listOf("Outillage", "Électricité", "Plomberie", "Visserie & Fixation"),
                quickUnits = listOf("sac", "kg", "mètre", "litre", "barre", "rouleau", "boîte", "pièce"),
                sectorFeatures = SectorFeatures(hasFractionalUnits = true, hasJobSiteQuotes = true),
                emoji = "🔨",
                defaultProducts = listOf(
                    ProductEntity(name = "Sac de ciment CIMBURKINA 50kg", category = "Gros oeuvre", buyPrice = 6200, sellPrice = 7200, stockQuantity = 35, minStockAlert = 10, unit = "sac", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Ampoule LED 12W économique", category = "Électricité", buyPrice = 700, sellPrice = 1400, stockQuantity = 50, minStockAlert = 15, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Robinet chromé quart de tour", category = "Plomberie", buyPrice = 1800, sellPrice = 3200, stockQuantity = 14, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Marteau manche bois 500g", category = "Outillage", buyPrice = 2100, sellPrice = 3800, stockQuantity = 9, minStockAlert = 4, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Mètre ruban 5m professionnel", category = "Outillage", buyPrice = 1000, sellPrice = 2000, stockQuantity = 16, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Boîte de pointes 60mm (1kg)", category = "Visserie & Fixation", buyPrice = 800, sellPrice = 1400, stockQuantity = 28, minStockAlert = 8, unit = "kg", imageUri = "img_prod_quincaillerie")
                )
            )

            "Maçonnerie" -> BusinessTypeInfo(
                id = "Maçonnerie",
                title = "Maçonnerie, BTP & Chantier",
                iconName = "domain",
                description = "Entreprise de construction, maçonnerie, coulage béton, briques, devis et facturation chantier",
                defaultCategories = listOf("Liants & Ciments", "Agrégats & Sable", "Briques & Blocs", "Fers à béton", "Main d'œuvre & Coulage"),
                quickUnits = listOf("sac", "voyage", "brouette", "m³", "brique", "barre", "journée", "forfait"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasFractionalUnits = true),
                emoji = "🧱",
                defaultProducts = listOf(
                    ProductEntity(name = "Sac Ciment CIMBURKINA CPJ 42.5", category = "Liants & Ciments", buyPrice = 6300, sellPrice = 7300, stockQuantity = 50, minStockAlert = 15, unit = "sac", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Voyage Sable fin de fleuve (Benne 10 roues)", category = "Agrégats & Sable", buyPrice = 45000, sellPrice = 65000, stockQuantity = 6, minStockAlert = 2, unit = "voyage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Voyage Gravier concassé 15/25", category = "Agrégats & Sable", buyPrice = 55000, sellPrice = 75000, stockQuantity = 5, minStockAlert = 2, unit = "voyage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Briques creuses 15x20x40 (Lot de 100)", category = "Briques & Blocs", buyPrice = 28000, sellPrice = 38000, stockQuantity = 12, minStockAlert = 3, unit = "lot", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Fer à béton torsadé diam 10 (Barre 12m)", category = "Fers à béton", buyPrice = 3200, sellPrice = 4200, stockQuantity = 60, minStockAlert = 15, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Fer à béton torsadé diam 8 (Barre 12m)", category = "Fers à béton", buyPrice = 2100, sellPrice = 2900, stockQuantity = 80, minStockAlert = 20, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Main d'oeuvre coulage dalle & maçonnerie", category = "Main d'œuvre & Coulage", buyPrice = 0, sellPrice = 15000, stockQuantity = 100, minStockAlert = 1, unit = "journée", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait crépissage & finition au m²", category = "Main d'œuvre & Coulage", buyPrice = 0, sellPrice = 2500, stockQuantity = 200, minStockAlert = 1, unit = "m²", imageUri = "img_prod_quincaillerie")
                )
            )

            "Menuiserie" -> BusinessTypeInfo(
                id = "Menuiserie",
                title = "Menuiserie, Bois & Meubles",
                iconName = "carpenter",
                description = "Atelier de menuiserie bois, confections sur-mesure, meubles, portes et quincaillerie bois",
                defaultCategories = listOf("Planches & Chevrons", "Contreplaqués & Panneaux", "Serrures & Quincaillerie", "Colles & Finitions", "Confections"),
                quickUnits = listOf("planche", "chevron", "feuille", "mètre", "pièce", "forfait", "journée"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasVariations = true),
                emoji = "🪚",
                defaultProducts = listOf(
                    ProductEntity(name = "Planche bois rouge Samba 4m (Ép. 25mm)", category = "Planches & Chevrons", buyPrice = 3500, sellPrice = 5200, stockQuantity = 30, minStockAlert = 8, unit = "planche", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Chevron bois blanc 5x7x4m", category = "Planches & Chevrons", buyPrice = 1800, sellPrice = 2800, stockQuantity = 40, minStockAlert = 10, unit = "chevron", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Contreplaqué marine 15mm (244x122)", category = "Contreplaqués & Panneaux", buyPrice = 9500, sellPrice = 13500, stockQuantity = 15, minStockAlert = 4, unit = "feuille", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Colle à bois blanche forte 1kg", category = "Colles & Finitions", buyPrice = 1800, sellPrice = 3000, stockQuantity = 20, minStockAlert = 5, unit = "pot", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Serrure canon européen pour porte", category = "Serrures & Quincaillerie", buyPrice = 3500, sellPrice = 6000, stockQuantity = 12, minStockAlert = 3, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Vernis bois teck brillant 1L", category = "Colles & Finitions", buyPrice = 2800, sellPrice = 4500, stockQuantity = 16, minStockAlert = 4, unit = "flacon", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Confection porte isoplane bois massif", category = "Confections", buyPrice = 15000, sellPrice = 35000, stockQuantity = 10, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait pose & ajustage porte ou fenêtre", category = "Confections", buyPrice = 0, sellPrice = 10000, stockQuantity = 50, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            "Soudure" -> BusinessTypeInfo(
                id = "Soudure",
                title = "Soudure, Métallurgie & Ferronnerie",
                iconName = "hardware",
                description = "Atelier de soudure, grilles de sécurité, portails, charpentes métalliques et profilés",
                defaultCategories = listOf("Tubes carrés & Ronds", "Cornières & Profilés", "Tôles & Plaques", "Électrodes & Disques", "Ouvrages Métal"),
                quickUnits = listOf("barre", "paquet", "tôle", "kg", "mètre", "pièce", "forfait"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasFractionalUnits = true),
                emoji = "👨‍🏭",
                defaultProducts = listOf(
                    ProductEntity(name = "Tube carré acier 30x30x1.5 (Barre 6m)", category = "Tubes carrés & Ronds", buyPrice = 4200, sellPrice = 5800, stockQuantity = 25, minStockAlert = 6, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Tube rectangulaire 40x20x1.5 (Barre 6m)", category = "Tubes carrés & Ronds", buyPrice = 4800, sellPrice = 6500, stockQuantity = 20, minStockAlert = 5, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Cornière laminée 30x30x3 (Barre 6m)", category = "Cornières & Profilés", buyPrice = 3600, sellPrice = 5000, stockQuantity = 30, minStockAlert = 8, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Paquet d'électrodes rutiles 2.5mm (5kg)", category = "Électrodes & Disques", buyPrice = 5500, sellPrice = 7500, stockQuantity = 18, minStockAlert = 5, unit = "paquet", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Disque meuleuse 230mm métal épais", category = "Électrodes & Disques", buyPrice = 1200, sellPrice = 2000, stockQuantity = 40, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Tôle ondulée galvanisée 2m (Ép. 0.25mm)", category = "Tôles & Plaques", buyPrice = 3200, sellPrice = 4500, stockQuantity = 50, minStockAlert = 12, unit = "tôle", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Fabrication grille antivol fenêtre", category = "Ouvrages Métal", buyPrice = 8000, sellPrice = 22000, stockQuantity = 15, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait pose & scellement portail battant", category = "Ouvrages Métal", buyPrice = 0, sellPrice = 25000, stockQuantity = 20, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            "Plomberie" -> BusinessTypeInfo(
                id = "Plomberie",
                title = "Plomberie, Sanitaire & Tuyauterie",
                iconName = "plumbing",
                description = "Entreprise de plomberie sanitaire, canalisations, robinetterie, raccords et dépannages d'urgence",
                defaultCategories = listOf("Tuyaux PVC & PE", "Raccords & Vannes", "Robinetterie & Sanitaire", "Colles & Étanchéité", "Dépannages"),
                quickUnits = listOf("pièce", "barre", "rouleau", "pot", "mètre", "forfait", "dépannage"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasFractionalUnits = true),
                emoji = "🔧",
                defaultProducts = listOf(
                    ProductEntity(name = "Tuyau PVC pression DN 32 (Barre 4m)", category = "Tuyaux PVC & PE", buyPrice = 2200, sellPrice = 3400, stockQuantity = 25, minStockAlert = 6, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Tuyau PVC évacuation DN 100 (Barre 4m)", category = "Tuyaux PVC & PE", buyPrice = 3800, sellPrice = 5500, stockQuantity = 18, minStockAlert = 5, unit = "barre", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Vanne d'arrêt laiton quart de tour 1/2\"", category = "Raccords & Vannes", buyPrice = 1800, sellPrice = 3200, stockQuantity = 20, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Coude PVC pression 90° diam 32", category = "Raccords & Vannes", buyPrice = 300, sellPrice = 600, stockQuantity = 60, minStockAlert = 15, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Colle PVC forte pression Tangit 250g", category = "Colles & Étanchéité", buyPrice = 1500, sellPrice = 2500, stockQuantity = 15, minStockAlert = 4, unit = "pot", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Rouleau téflon ruban étanchéité", category = "Colles & Étanchéité", buyPrice = 200, sellPrice = 500, stockQuantity = 50, minStockAlert = 10, unit = "rouleau", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Robinet mélangeur lavabo chromé", category = "Robinetterie & Sanitaire", buyPrice = 7500, sellPrice = 13500, stockQuantity = 8, minStockAlert = 2, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait débouchage & réparation fuite eau", category = "Dépannages", buyPrice = 0, sellPrice = 12000, stockQuantity = 40, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            "Électricité" -> BusinessTypeInfo(
                id = "Électricité",
                title = "Électricité, Énergie & Solaire",
                iconName = "electric_bolt",
                description = "Installations électriques bâtiment, énergie solaire, câbles, disjoncteurs et dépannages",
                defaultCategories = listOf("Câbles & Gaines", "Disjoncteurs & Tableaux", "Prises & Interrupteurs", "Panneaux & Solaire", "Éclairage LED", "Prestations & Câblage"),
                quickUnits = listOf("pièce", "rouleau", "mètre", "kit", "ampoule", "forfait", "diagnostic"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasFractionalUnits = true),
                emoji = "💡",
                defaultProducts = listOf(
                    ProductEntity(name = "Rouleau fil cuivre VGV 1.5mm² (100m)", category = "Câbles & Gaines", buyPrice = 12500, sellPrice = 17500, stockQuantity = 12, minStockAlert = 3, unit = "rouleau", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Rouleau fil cuivre VGV 2.5mm² (100m)", category = "Câbles & Gaines", buyPrice = 18500, sellPrice = 24500, stockQuantity = 10, minStockAlert = 3, unit = "rouleau", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Disjoncteur divisionnaire 16A Schneider", category = "Disjoncteurs & Tableaux", buyPrice = 2200, sellPrice = 3800, stockQuantity = 25, minStockAlert = 6, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Prise de courant murale encastrée avec terre", category = "Prises & Interrupteurs", buyPrice = 900, sellPrice = 1600, stockQuantity = 45, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Panneau solaire 250W Monocristallin", category = "Panneaux & Solaire", buyPrice = 42000, sellPrice = 58000, stockQuantity = 8, minStockAlert = 2, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Régulateur de charge solaire MPPT 30A", category = "Panneaux & Solaire", buyPrice = 14000, sellPrice = 22000, stockQuantity = 6, minStockAlert = 2, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Projecteur solaire LED 100W autonome", category = "Éclairage LED", buyPrice = 16000, sellPrice = 26000, stockQuantity = 10, minStockAlert = 3, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait câblage tableau & diagnostic réseau", category = "Prestations & Câblage", buyPrice = 0, sellPrice = 20000, stockQuantity = 30, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            "Mécanique" -> BusinessTypeInfo(
                id = "Mécanique",
                title = "Garage, Mécanique Auto & Moto",
                iconName = "car_repair",
                description = "Atelier de réparation auto-moto, entretien périodique, vidange, pièces de rechange et pneumatiques",
                defaultCategories = listOf("Huiles & Lubrifiants", "Filtres & Allumage", "Freinage & Suspension", "Pneumatiques", "Prestations & Révision"),
                quickUnits = listOf("bidon", "pièce", "litre", "paire", "service", "forfait"),
                sectorFeatures = SectorFeatures(hasJobSiteQuotes = true, hasOperatorStylist = true, hasFractionalUnits = true),
                emoji = "🚗",
                defaultProducts = listOf(
                    ProductEntity(name = "Huile moteur 20W50 Total Quartz (5L)", category = "Huiles & Lubrifiants", buyPrice = 14500, sellPrice = 19500, stockQuantity = 15, minStockAlert = 4, unit = "bidon", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Huile moto 4T 10W40 (Bidon 1L)", category = "Huiles & Lubrifiants", buyPrice = 2200, sellPrice = 3500, stockQuantity = 30, minStockAlert = 8, unit = "bidon", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Filtre à huile universel modèle Toyota", category = "Filtres & Allumage", buyPrice = 2000, sellPrice = 4000, stockQuantity = 20, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Bougie d'allumage haute résistance NGK", category = "Filtres & Allumage", buyPrice = 1000, sellPrice = 2000, stockQuantity = 40, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Plaquettes de frein avant (Paire complète)", category = "Freinage & Suspension", buyPrice = 6500, sellPrice = 11000, stockQuantity = 10, minStockAlert = 3, unit = "paire", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Pneu moto 17\" Tubeless résistant", category = "Pneumatiques", buyPrice = 9000, sellPrice = 14500, stockQuantity = 12, minStockAlert = 4, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Forfait vidange complète moteur + filtres", category = "Prestations & Révision", buyPrice = 0, sellPrice = 5000, stockQuantity = 50, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Diagnostic mécanique & parallélisme train avant", category = "Prestations & Révision", buyPrice = 0, sellPrice = 15000, stockQuantity = 25, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            "Boulangerie" -> BusinessTypeInfo(
                id = "Boulangerie",
                title = "Boulangerie & Pâtisserie",
                iconName = "bakery_dining",
                description = "Fournil artisanal, baguettes chaudes, viennoiseries, gâteaux sur commande, farines et traiteur",
                defaultCategories = listOf("Pains & Baguettes", "Viennoiseries", "Pâtisserie & Gâteaux", "Snacks salés", "Matières premières"),
                quickUnits = listOf("baguette", "croissant", "miche", "gâteau", "part", "pièce", "sac", "lot"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true, hasVariations = true),
                emoji = "🥖",
                defaultProducts = listOf(
                    ProductEntity(name = "Baguette tradition croustillante", category = "Pains & Baguettes", buyPrice = 100, sellPrice = 150, stockQuantity = 150, minStockAlert = 30, unit = "baguette", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Croissant pur beurre doré", category = "Viennoiseries", buyPrice = 150, sellPrice = 250, stockQuantity = 60, minStockAlert = 15, unit = "croissant", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain au chocolat feuilleté", category = "Viennoiseries", buyPrice = 160, sellPrice = 300, stockQuantity = 50, minStockAlert = 12, unit = "pièce", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Pain de mie tranché familial", category = "Pains & Baguettes", buyPrice = 400, sellPrice = 750, stockQuantity = 25, minStockAlert = 5, unit = "miche", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Gâteau anniversaire chocolat 8 parts", category = "Pâtisserie & Gâteaux", buyPrice = 4000, sellPrice = 9000, stockQuantity = 6, minStockAlert = 2, unit = "gâteau", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Brioche tressée au sucre", category = "Viennoiseries", buyPrice = 300, sellPrice = 600, stockQuantity = 20, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Farine de blé boulangère T55 (Sac 25kg)", category = "Matières premières", buyPrice = 12000, sellPrice = 15500, stockQuantity = 10, minStockAlert = 3, unit = "sac", imageUri = "img_prod_epicerie")
                )
            )

            "Couture" -> BusinessTypeInfo(
                id = "Couture",
                title = "Couture, Stylisme & Mercerie",
                iconName = "checkroom",
                description = "Atelier de confection sur-mesure, broderie bazin, retouches, tenues d'apparat, boutons et mercerie",
                defaultCategories = listOf("Confections Hommes", "Confections Dames", "Retouches & Ajustages", "Mercerie & Fils", "Tissus & Bazins"),
                quickUnits = listOf("tenue", "confection", "mètre", "bobine", "pièce", "lot", "forfait", "retouche"),
                sectorFeatures = SectorFeatures(hasVariations = true, hasJobSiteQuotes = true, hasOperatorStylist = true),
                emoji = "✂️",
                defaultProducts = listOf(
                    ProductEntity(name = "Confection complet boubou Bazin 3 pièces", category = "Confections Hommes", buyPrice = 5000, sellPrice = 25000, stockQuantity = 20, minStockAlert = 2, unit = "tenue", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Robe moderne pagne Faso Danfani", category = "Confections Dames", buyPrice = 4000, sellPrice = 18000, stockQuantity = 15, minStockAlert = 2, unit = "confection", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Retouche ourlet pantalon ou robe", category = "Retouches & Ajustages", buyPrice = 0, sellPrice = 1000, stockQuantity = 100, minStockAlert = 1, unit = "retouche", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Fil à coudre haute ténacité (Bobine)", category = "Mercerie & Fils", buyPrice = 250, sellPrice = 500, stockQuantity = 80, minStockAlert = 15, unit = "bobine", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Fermeture à glissière métallique 60cm", category = "Mercerie & Fils", buyPrice = 200, sellPrice = 500, stockQuantity = 60, minStockAlert = 10, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Boutons de manchette nacrés (Lot de 10)", category = "Mercerie & Fils", buyPrice = 400, sellPrice = 1000, stockQuantity = 40, minStockAlert = 8, unit = "lot", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Tissu Bazin riche Getzner teinté (Mètre)", category = "Tissus & Bazins", buyPrice = 4500, sellPrice = 7500, stockQuantity = 30, minStockAlert = 5, unit = "mètre", imageUri = "img_prod_mode")
                )
            )

            "Pressing" -> BusinessTypeInfo(
                id = "Pressing",
                title = "Pressing & Blanchisserie",
                iconName = "local_laundry_service",
                description = "Nettoyage à sec, repassage vapeur, couettes, draps d'hôtels, détachage express et gestion de linge",
                defaultCategories = listOf("Vêtements quotidiens", "Costumes & Cérémonie", "Linge de maison & Couettes", "Prestations Express"),
                quickUnits = listOf("pièce", "costume", "couette", "drap", "kg", "forfait", "paire"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true, hasJobSiteQuotes = true),
                emoji = "👔",
                defaultProducts = listOf(
                    ProductEntity(name = "Nettoyage complet costume 2 pièces", category = "Costumes & Cérémonie", buyPrice = 300, sellPrice = 2500, stockQuantity = 80, minStockAlert = 1, unit = "costume", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Repassage vapeur chemise ou pantalon", category = "Vêtements quotidiens", buyPrice = 50, sellPrice = 500, stockQuantity = 150, minStockAlert = 1, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Lavage grand boubou brodé Bazin", category = "Costumes & Cérémonie", buyPrice = 400, sellPrice = 3500, stockQuantity = 50, minStockAlert = 1, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Lavage grande couette 2 places / édredon", category = "Linge de maison & Couettes", buyPrice = 800, sellPrice = 4000, stockQuantity = 40, minStockAlert = 1, unit = "couette", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Lavage au kilo linge quotidien (kg)", category = "Vêtements quotidiens", buyPrice = 200, sellPrice = 1200, stockQuantity = 200, minStockAlert = 1, unit = "kg", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Forfait détachage express vêtement délicat", category = "Prestations Express", buyPrice = 100, sellPrice = 1500, stockQuantity = 60, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_mode")
                )
            )

            "Boucherie" -> BusinessTypeInfo(
                id = "Boucherie",
                title = "Boucherie & Charcuterie",
                iconName = "restaurant",
                description = "Viande fraîche de bœuf, mouton, volailles, découpe sur commande, abats et charcuterie",
                defaultCategories = listOf("Viande de Bœuf", "Mouton & Chèvre", "Volailles fraîches", "Abats & Grillades", "Saucisses & Préparations"),
                quickUnits = listOf("kg", "demi-kg", "morceau", "gigot", "pièce", "barquette", "tas"),
                sectorFeatures = SectorFeatures(hasFractionalUnits = true, hasSpeedCashShortcuts = true),
                emoji = "🥩",
                defaultProducts = listOf(
                    ProductEntity(name = "Viande de bœuf avec os (kg)", category = "Viande de Bœuf", buyPrice = 2200, sellPrice = 2800, stockQuantity = 60, minStockAlert = 10, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Filet de bœuf sans os 1er choix (kg)", category = "Viande de Bœuf", buyPrice = 3000, sellPrice = 4000, stockQuantity = 35, minStockAlert = 5, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Gigot de mouton frais entier (kg)", category = "Mouton & Chèvre", buyPrice = 3500, sellPrice = 4500, stockQuantity = 20, minStockAlert = 4, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Poulet fermier découpé prêt à cuire", category = "Volailles fraîches", buyPrice = 2200, sellPrice = 3200, stockQuantity = 30, minStockAlert = 6, unit = "pièce", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Foie et rognons de bœuf frais (kg)", category = "Abats & Grillades", buyPrice = 2500, sellPrice = 3500, stockQuantity = 15, minStockAlert = 3, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Saucisses fraîches bœuf épicées (kg)", category = "Saucisses & Préparations", buyPrice = 2800, sellPrice = 4200, stockQuantity = 25, minStockAlert = 5, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Viande hachée pur bœuf assaisonnée (kg)", category = "Saucisses & Préparations", buyPrice = 2600, sellPrice = 3800, stockQuantity = 30, minStockAlert = 5, unit = "kg", imageUri = "img_prod_resto")
                )
            )

            "Poissonnerie" -> BusinessTypeInfo(
                id = "Poissonnerie",
                title = "Poissonnerie & Produits Halieutiques",
                iconName = "set_meal",
                description = "Poissons frais du fleuve et mer, carpes, capitaines, tilapias, poissons fumés et crevettes",
                defaultCategories = listOf("Poissons d'eau douce", "Poissons de mer", "Poissons fumés & séchés", "Crustacés & Fruits de mer"),
                quickUnits = listOf("kg", "carton", "tas", "pièce", "barquette", "sachet"),
                sectorFeatures = SectorFeatures(hasFractionalUnits = true, hasSpeedCashShortcuts = true),
                emoji = "🐟",
                defaultProducts = listOf(
                    ProductEntity(name = "Capitaine frais du fleuve (kg)", category = "Poissons d'eau douce", buyPrice = 3200, sellPrice = 4500, stockQuantity = 25, minStockAlert = 5, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Carpe fraîche vivante de retenue (kg)", category = "Poissons d'eau douce", buyPrice = 2000, sellPrice = 3000, stockQuantity = 40, minStockAlert = 8, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Tilapia rouge frais entier (kg)", category = "Poissons d'eau douce", buyPrice = 1800, sellPrice = 2600, stockQuantity = 35, minStockAlert = 6, unit = "kg", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Poisson fumé traditionnel de Bagré (Tas)", category = "Poissons fumés & séchés", buyPrice = 1200, sellPrice = 2000, stockQuantity = 50, minStockAlert = 10, unit = "tas", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Chinchard surgelé (Carton de 10kg)", category = "Poissons de mer", buyPrice = 11000, sellPrice = 14500, stockQuantity = 15, minStockAlert = 3, unit = "carton", imageUri = "img_prod_resto"),
                    ProductEntity(name = "Crevettes fraîches calibre moyen (kg)", category = "Crustacés & Fruits de mer", buyPrice = 4500, sellPrice = 7000, stockQuantity = 12, minStockAlert = 2, unit = "kg", imageUri = "img_prod_resto")
                )
            )

            "Agro-pastoral" -> BusinessTypeInfo(
                id = "Agro-pastoral",
                title = "Agro-pastoral, Élevage & Aliments Bétail",
                iconName = "agriculture",
                description = "Provende volaille, tourteau de coton, céréales concassées, sel gemme, engrais et intrants",
                defaultCategories = listOf("Aliments Volailles", "Aliments Ruminants & Bétail", "Céréales & Sons", "Engrais & Intrants", "Vitamines & Soins"),
                quickUnits = listOf("sac", "kg", "tonne", "bloc", "boîte", "bidon", "flacon"),
                sectorFeatures = SectorFeatures(hasFractionalUnits = true, hasJobSiteQuotes = true),
                emoji = "🌾",
                defaultProducts = listOf(
                    ProductEntity(name = "Tourteau de coton SN-CITEC (Sac 50kg)", category = "Aliments Ruminants & Bétail", buyPrice = 11500, sellPrice = 13500, stockQuantity = 40, minStockAlert = 8, unit = "sac", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Aliment démarrage poussins provende (50kg)", category = "Aliments Volailles", buyPrice = 14000, sellPrice = 17000, stockQuantity = 25, minStockAlert = 5, unit = "sac", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Aliment finition poulets de chair (50kg)", category = "Aliments Volailles", buyPrice = 13500, sellPrice = 16500, stockQuantity = 30, minStockAlert = 6, unit = "sac", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Maïs blanc concassé sec (Sac 100kg)", category = "Céréales & Sons", buyPrice = 16000, sellPrice = 19500, stockQuantity = 20, minStockAlert = 4, unit = "sac", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Bloc de sel minéralisé à lécher (5kg)", category = "Aliments Ruminants & Bétail", buyPrice = 1800, sellPrice = 2800, stockQuantity = 35, minStockAlert = 6, unit = "bloc", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Engrais NPK 14-23-14 certifié (Sac 50kg)", category = "Engrais & Intrants", buyPrice = 22000, sellPrice = 26000, stockQuantity = 30, minStockAlert = 5, unit = "sac", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Complexe multivitaminé aviaire (Boîte 100g)", category = "Vitamines & Soins", buyPrice = 1200, sellPrice = 2000, stockQuantity = 40, minStockAlert = 8, unit = "boîte", imageUri = "img_prod_epicerie")
                )
            )

            "Informatique" -> BusinessTypeInfo(
                id = "Informatique",
                title = "Informatique, Téléphonie & Réparation",
                iconName = "smartphone",
                description = "Smartphones, ordinateurs portables, accessoires connectés, réparation écrans et microsoudure",
                defaultCategories = listOf("Smartphones & Téléphones", "Accessoires Mobiles", "Ordinateurs & Périphériques", "Réparations & Dépannages"),
                quickUnits = listOf("pièce", "unité", "forfait", "câble", "écran", "réparation"),
                sectorFeatures = SectorFeatures(hasVariations = true, hasJobSiteQuotes = true, hasOperatorStylist = true),
                emoji = "📱",
                defaultProducts = listOf(
                    ProductEntity(name = "Smartphone 4G 64GB double SIM", category = "Smartphones & Téléphones", buyPrice = 42000, sellPrice = 55000, stockQuantity = 12, minStockAlert = 3, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Verre trempé 9D incassable tous modèles", category = "Accessoires Mobiles", buyPrice = 500, sellPrice = 1500, stockQuantity = 80, minStockAlert = 15, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Chargeur rapide 33W Type-C original", category = "Accessoires Mobiles", buyPrice = 2200, sellPrice = 4500, stockQuantity = 30, minStockAlert = 6, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Câble blindé USB-C tresse nylon 1.5m", category = "Accessoires Mobiles", buyPrice = 800, sellPrice = 2000, stockQuantity = 50, minStockAlert = 10, unit = "câble", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Écouteurs sans fil Bluetooth stéréo", category = "Accessoires Mobiles", buyPrice = 3800, sellPrice = 7500, stockQuantity = 20, minStockAlert = 4, unit = "pièce", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Remplacement écran complet tactile smartphone", category = "Réparations & Dépannages", buyPrice = 8000, sellPrice = 18000, stockQuantity = 15, minStockAlert = 2, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Flashage, déblocage système Android ou iOS", category = "Réparations & Dépannages", buyPrice = 0, sellPrice = 5000, stockQuantity = 50, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Clé USB 64GB Kingston métal résistante", category = "Ordinateurs & Périphériques", buyPrice = 3000, sellPrice = 5500, stockQuantity = 25, minStockAlert = 5, unit = "pièce", imageUri = "img_prod_quincaillerie")
                )
            )

            "Librairie" -> BusinessTypeInfo(
                id = "Librairie",
                title = "Librairie, Papeterie & Bureautique",
                iconName = "menu_book",
                description = "Fournitures scolaires, livres, cahiers, rames de papier A4, bureautique, photocopies et reliures",
                defaultCategories = listOf("Papeterie & Cahiers", "Écriture & Traçage", "Bureautique & Rames", "Services Reprographie", "Livres Scolaires"),
                quickUnits = listOf("cahier", "rame", "pièce", "paquet", "boîte", "copie", "reliure"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true),
                emoji = "📚",
                defaultProducts = listOf(
                    ProductEntity(name = "Rame papier blanc A4 80g Double A (500f)", category = "Bureautique & Rames", buyPrice = 3200, sellPrice = 4500, stockQuantity = 40, minStockAlert = 10, unit = "rame", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Cahier grand format 200 pages quadrillé", category = "Papeterie & Cahiers", buyPrice = 600, sellPrice = 1000, stockQuantity = 100, minStockAlert = 20, unit = "cahier", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Cahier 100 pages petit format", category = "Papeterie & Cahiers", buyPrice = 250, sellPrice = 400, stockQuantity = 150, minStockAlert = 30, unit = "cahier", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Boîte de 50 stylos à bille bleus BIC", category = "Écriture & Traçage", buyPrice = 4500, sellPrice = 6500, stockQuantity = 15, minStockAlert = 3, unit = "boîte", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Boîte d'instruments géométrie métal Maped", category = "Écriture & Traçage", buyPrice = 900, sellPrice = 1600, stockQuantity = 30, minStockAlert = 6, unit = "pièce", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Service Photocopie noir et blanc A4", category = "Services Reprographie", buyPrice = 10, sellPrice = 25, stockQuantity = 500, minStockAlert = 50, unit = "copie", imageUri = "img_prod_epicerie"),
                    ProductEntity(name = "Forfait Reliure spirale plastique document", category = "Services Reprographie", buyPrice = 200, sellPrice = 750, stockQuantity = 80, minStockAlert = 10, unit = "reliure", imageUri = "img_prod_epicerie")
                )
            )

            "Bijouterie" -> BusinessTypeInfo(
                id = "Bijouterie",
                title = "Bijouterie, Horlogerie & Joaillerie",
                iconName = "diamond",
                description = "Bijoux en or, argent, parures traditionnelles, perles de mariage, montres, réparations et gravures",
                defaultCategories = listOf("Bijoux en Or 18k", "Bijoux en Argent 925", "Parures & Perles Tradition", "Montres & Horlogerie", "Prestations & Gravures"),
                quickUnits = listOf("pièce", "gramme", "parure", "paire", "forfait", "service"),
                sectorFeatures = SectorFeatures(hasVariations = true, hasFractionalUnits = true, hasJobSiteQuotes = true),
                emoji = "💍",
                defaultProducts = listOf(
                    ProductEntity(name = "Chaîne or 18k maille forçat (le gramme)", category = "Bijoux en Or 18k", buyPrice = 32000, sellPrice = 42000, stockQuantity = 20, minStockAlert = 3, unit = "gramme", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Bague solitaire élégante argent massif 925", category = "Bijoux en Argent 925", buyPrice = 6000, sellPrice = 12000, stockQuantity = 15, minStockAlert = 3, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Boucles d'oreilles créoles argent ciselé", category = "Bijoux en Argent 925", buyPrice = 4500, sellPrice = 9000, stockQuantity = 18, minStockAlert = 4, unit = "paire", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Parure collier + bracelet perles de mariage", category = "Parures & Perles Tradition", buyPrice = 15000, sellPrice = 30000, stockQuantity = 8, minStockAlert = 2, unit = "parure", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Montre quartz bracelet acier étanche", category = "Montres & Horlogerie", buyPrice = 7000, sellPrice = 15000, stockQuantity = 12, minStockAlert = 3, unit = "pièce", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Remplacement pile montre + test étanchéité", category = "Prestations & Gravures", buyPrice = 500, sellPrice = 1500, stockQuantity = 50, minStockAlert = 5, unit = "service", imageUri = "img_prod_mode"),
                    ProductEntity(name = "Gravure personnalisée prénom ou date", category = "Prestations & Gravures", buyPrice = 0, sellPrice = 2500, stockQuantity = 40, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_mode")
                )
            )

            "Lavage Auto-Moto" -> BusinessTypeInfo(
                id = "Lavage Auto-Moto",
                title = "Lavage Auto-Moto & Esthétique Véhicule",
                iconName = "local_car_wash",
                description = "Station de lavage haute pression, mousse active karcher, intérieur/extérieur moto, auto, 4x4",
                defaultCategories = listOf("Lavage Deux-Roues", "Lavage Véhicules Légers", "Lavage 4x4 & Utilitaires", "Soins & Esthétique"),
                quickUnits = listOf("lavage", "forfait", "service", "passage", "pièce"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true, hasOperatorStylist = true),
                emoji = "🚿",
                defaultProducts = listOf(
                    ProductEntity(name = "Lavage moto express karcher", category = "Lavage Deux-Roues", buyPrice = 100, sellPrice = 500, stockQuantity = 200, minStockAlert = 1, unit = "lavage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Lavage moto complet mousse + brillant pneus", category = "Lavage Deux-Roues", buyPrice = 200, sellPrice = 1000, stockQuantity = 150, minStockAlert = 1, unit = "lavage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Lavage simple extérieur voiture citadine", category = "Lavage Véhicules Légers", buyPrice = 300, sellPrice = 1500, stockQuantity = 100, minStockAlert = 1, unit = "lavage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Lavage complet voiture (Intérieur + Extérieur)", category = "Lavage Véhicules Légers", buyPrice = 500, sellPrice = 3000, stockQuantity = 80, minStockAlert = 1, unit = "lavage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Lavage complet 4x4, SUV ou Pick-up", category = "Lavage 4x4 & Utilitaires", buyPrice = 800, sellPrice = 5000, stockQuantity = 50, minStockAlert = 1, unit = "lavage", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Nettoyage vapeur en profondeur sièges auto", category = "Soins & Esthétique", buyPrice = 1500, sellPrice = 12000, stockQuantity = 25, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie"),
                    ProductEntity(name = "Rénovation optiques de phares jaunis", category = "Soins & Esthétique", buyPrice = 1000, sellPrice = 7500, stockQuantity = 30, minStockAlert = 1, unit = "forfait", imageUri = "img_prod_quincaillerie")
                )
            )

            else -> BusinessTypeInfo(
                id = "Commerce général",
                title = "Commerce général & Épicerie",
                iconName = "category",
                description = "Alimentation générale, épicerie, produits de première nécessité, vente détail et gros",
                defaultCategories = listOf("Alimentation", "Boissons", "Entretien & Savons", "Divers"),
                quickUnits = listOf("pièce", "sac", "bouteille", "paquet", "boîte", "carton", "morceau", "kg", "litre"),
                sectorFeatures = SectorFeatures(hasSpeedCashShortcuts = true, hasFractionalUnits = true),
                emoji = "🛒",
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
