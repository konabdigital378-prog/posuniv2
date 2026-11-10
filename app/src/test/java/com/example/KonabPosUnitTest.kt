package com.example

import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.viewmodel.CartItem
import com.example.util.BusinessTypePresets
import com.example.util.CurrencyFormatter
import com.example.util.ReceiptPrinterHelper
import org.junit.Assert.*
import org.junit.Test

class KonabPosUnitTest {

    @Test
    fun testCurrencyFormatting() {
        assertEquals("4 500 FCFA", CurrencyFormatter.formatFCFA(4500L))
        assertEquals("0 FCFA", CurrencyFormatter.formatFCFA(0L))
        assertEquals("1 000 000 FCFA", CurrencyFormatter.formatFCFA(1000000L))
    }

    @Test
    fun testBusinessTypePresets() {
        val types = BusinessTypePresets.getAllTypes()
        assertTrue(types.contains("Boutique"))
        assertTrue(types.contains("Restaurant"))
        assertTrue(types.contains("Pharmacie"))
        assertTrue(types.contains("Quincaillerie"))

        val boutique = BusinessTypePresets.getPreset("Boutique")
        assertFalse(boutique.defaultProducts.isEmpty())
        assertTrue(boutique.defaultProducts.any { it.name.contains("T-shirt", ignoreCase = true) })
    }

    @Test
    fun testCartCalculations() {
        val product1 = ProductEntity(name = "T-shirt", category = "Vêtements", buyPrice = 1000, sellPrice = 2000)
        val product2 = ProductEntity(name = "Casquette", category = "Accessoires", buyPrice = 1500, sellPrice = 2500)

        val cart = listOf(
            CartItem(product = product1, quantity = 2), // 4000
            CartItem(product = product2, quantity = 1)  // 2500
        )

        val subtotal = cart.sumOf { it.totalPrice }
        assertEquals(6500L, subtotal)

        val discount = 500L
        val total = maxOf(0L, subtotal - discount)
        assertEquals(6000L, total)
    }

    @Test
    fun testReceiptTextGeneration() {
        val shop = ShopProfileEntity(name = "Boutique Kaba", phone = "65413799", address = "Bobo-Dioulasso")
        val sale = SaleEntity(
            transactionNumber = "20261030-0001",
            subtotal = 4500,
            totalAmount = 4500,
            paymentMethod = "Espèces"
        )
        val items = listOf(
            SaleItemEntity(saleId = 1, productId = 1, productName = "T-shirt", quantity = 1, unitPrice = 2000, unitBuyPrice = 1000, totalPrice = 2000),
            SaleItemEntity(saleId = 1, productId = 2, productName = "Casquette", quantity = 1, unitPrice = 2500, unitBuyPrice = 1400, totalPrice = 2500)
        )

        val text = ReceiptPrinterHelper.generateReceiptText(shop, sale, items)
        assertTrue(text.contains("BOUTIQUE KABA"))
        assertTrue(text.contains("20261030-0001"))
        assertTrue(text.contains("4 500 FCFA"))
        assertTrue(text.contains("Espèces"))
    }

    @Test
    fun testLocalAdminAccountDefaults() {
        val admin = com.example.data.local.datastore.LocalAdminAccount(
            adminName = "Moussa Traoré",
            adminPhone = "65413799",
            adminPin = "4321",
            role = "ADMIN",
            isSetupCompleted = true,
            shopName = "Boutique Kaba",
            businessType = "Boutique"
        )
        assertEquals("Moussa Traoré", admin.adminName)
        assertEquals("65413799", admin.adminPhone)
        assertEquals("4321", admin.adminPin)
        assertEquals("ADMIN", admin.role)
        assertTrue(admin.isSetupCompleted)
    }

    @Test
    fun testSetupWizardSteps() {
        val steps = com.example.ui.screens.SetupWizardStep.values()
        assertEquals(7, steps.size)
        assertEquals(com.example.ui.screens.SetupWizardStep.WELCOME, steps[0])
        assertEquals(com.example.ui.screens.SetupWizardStep.BUSINESS_INFO, steps[1])
        assertEquals(com.example.ui.screens.SetupWizardStep.ACTIVITY_TYPE, steps[2])
        assertEquals(com.example.ui.screens.SetupWizardStep.CUSTOMIZATION, steps[3])
        assertEquals(com.example.ui.screens.SetupWizardStep.ADMIN_ACCOUNT, steps[4])
        assertEquals(com.example.ui.screens.SetupWizardStep.READY, steps[5])
        assertEquals(com.example.ui.screens.SetupWizardStep.REGISTER_SHOP, steps[6])
    }

    @Test
    fun testSetupWizardDestinations() {
        val destinations = com.example.ui.navigation.SetupWizardDestination.allSteps
        assertEquals(7, destinations.size)
        assertEquals("setup/welcome", com.example.ui.navigation.SetupWizardDestination.Welcome.route)
        assertEquals("setup/business_info", com.example.ui.navigation.SetupWizardDestination.BusinessInfo.route)
        assertEquals("setup/activity_type", com.example.ui.navigation.SetupWizardDestination.ActivityType.route)
        assertEquals("setup/customization", com.example.ui.navigation.SetupWizardDestination.Customization.route)
        assertEquals("setup/admin_account", com.example.ui.navigation.SetupWizardDestination.AdminAccount.route)
        assertEquals("setup/ready", com.example.ui.navigation.SetupWizardDestination.Ready.route)
        assertEquals("setup/register_shop", com.example.ui.navigation.SetupWizardDestination.RegisterShop.route)

        assertEquals(
            com.example.ui.navigation.SetupWizardDestination.ActivityType,
            com.example.ui.navigation.SetupWizardDestination.fromRoute("setup/activity_type")
        )
        assertEquals(
            com.example.ui.navigation.SetupWizardDestination.AdminAccount,
            com.example.ui.navigation.SetupWizardDestination.fromRoute("setup/admin_account")
        )
        assertEquals(
            com.example.ui.navigation.SetupWizardDestination.RegisterShop,
            com.example.ui.navigation.SetupWizardDestination.fromRoute("setup/register_shop")
        )
    }

    @Test
    fun testBarcodeLookup() {
        val p1 = ProductEntity(name = "Savon Citron", category = "Hygiène", buyPrice = 300, sellPrice = 500, barcode = "618110001234")
        val p2 = ProductEntity(name = "Riz 5kg", category = "Alimentation", buyPrice = 2800, sellPrice = 3500, barcode = "618110005678")
        val catalog = listOf(p1, p2)

        val scannedCode = "618110001234"
        val found = catalog.find { it.barcode.equals(scannedCode.trim(), ignoreCase = true) }
        assertNotNull(found)
        assertEquals("Savon Citron", found?.name)

        val unknownCode = "999999999999"
        val notFound = catalog.find { it.barcode.equals(unknownCode.trim(), ignoreCase = true) }
        assertNull(notFound)
    }

    @Test
    fun testProductImageHelper() {
        val clothingRes = com.example.util.ProductImageHelper.getFallbackDrawableForProduct("Chemise homme", "Vêtements")
        assertEquals(R.drawable.img_prod_mode, clothingRes)

        val foodRes = com.example.util.ProductImageHelper.getFallbackDrawableForProduct("Riz blanc 5kg", "Alimentation")
        assertEquals(R.drawable.img_prod_epicerie, foodRes)

        val drinkRes = com.example.util.ProductImageHelper.getFallbackDrawableForProduct("Jus de Bissap", "Boissons")
        assertEquals(R.drawable.img_prod_boisson, drinkRes)

        val toolRes = com.example.util.ProductImageHelper.getFallbackDrawableForProduct("Marteau manche bois", "Outillage")
        assertEquals(R.drawable.img_prod_quincaillerie, toolRes)

        val resolved = com.example.util.ProductImageHelper.resolveDrawableRes("img_prod_resto")
        assertEquals(R.drawable.img_prod_resto, resolved)
    }

    @Test
    fun testShopLogoHelperAndEntity() {
        val defaultProfile = ShopProfileEntity(name = "Boutique Faso")
        assertNull(defaultProfile.logoUri)

        val customProfile = defaultProfile.copy(logoUri = "file:///data/user/0/com.example/files/logos/shop_logo.jpg")
        assertEquals("file:///data/user/0/com.example/files/logos/shop_logo.jpg", customProfile.logoUri)

        val presets = com.example.util.ShopLogoHelper.PRESET_LOGOS
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.any { it.key == "drawable:ic_konab_logo" })

        val resolved = com.example.util.ShopLogoHelper.resolvePresetDrawable("drawable:ic_konab_logo")
        assertEquals(R.drawable.ic_konab_logo, resolved)

        val resolvedCommerce = com.example.util.ShopLogoHelper.resolvePresetDrawable("drawable:burkina_commerce_faso")
        assertEquals(R.drawable.burkina_commerce_faso, resolvedCommerce)

        val resolvedNull = com.example.util.ShopLogoHelper.resolvePresetDrawable(null)
        assertNull(resolvedNull)
    }

    @Test
    fun testAgentDirectoryAndActivation() {
        val agent1 = com.example.ui.screens.AgentDirectory.findAgent("AGT-101", "1234")
        assertNotNull(agent1)
        assertEquals("Moussa Ouédraogo", agent1?.name)

        val invalidAgent = com.example.ui.screens.AgentDirectory.findAgent("UNKNOWN", "0000")
        assertNull(invalidAgent)

        val supervisor = com.example.ui.screens.AgentDirectory.findAgent("SUPERVISEUR", "9999")
        assertNotNull(supervisor)
        assertEquals("Direction Konab POS", supervisor?.name)
    }
}


