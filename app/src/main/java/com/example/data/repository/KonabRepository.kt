package com.example.data.repository

import com.example.data.local.dao.KonabDao
import com.example.data.local.entity.*
import com.example.util.BusinessTypePresets
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.local.datastore.KonabDataStore
import com.example.data.local.datastore.LocalAdminAccount

class KonabRepository(
    private val dao: KonabDao,
    private val dataStore: KonabDataStore
) {

    val adminAccount: Flow<LocalAdminAccount> = dataStore.adminAccountFlow
    val isSetupCompleted: Flow<Boolean> = dataStore.isSetupCompletedFlow
    val shopProfile: Flow<ShopProfileEntity?> = dao.getShopProfile()
    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()
    val availableProducts: Flow<List<ProductEntity>> = dao.getAvailableProducts()
    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()
    val debtorCustomers: Flow<List<CustomerEntity>> = dao.getDebtorCustomers()
    val allSuppliers: Flow<List<SupplierEntity>> = dao.getAllSuppliers()
    val recentSales: Flow<List<SaleEntity>> = dao.getRecentSales()
    val allSales: Flow<List<SaleEntity>> = dao.getAllSales()
    val allSaleItems: Flow<List<SaleItemEntity>> = dao.getAllSaleItems()
    val allCashMovements: Flow<List<CashMovementEntity>> = dao.getAllCashMovements()
    val activeCashSession: Flow<CashSessionEntity?> = dao.getActiveCashSession()
    val allPurchases: Flow<List<PurchaseEntity>> = dao.getAllPurchases()
    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()
    val unreadNotificationsCount: Flow<Int> = dao.getUnreadNotificationsCount()

    suspend fun getAllNotificationsOnce(): List<NotificationEntity> = dao.getAllNotificationsOnce()
    suspend fun insertNotification(notification: NotificationEntity): Long = dao.insertNotification(notification)
    suspend fun insertNotifications(notifications: List<NotificationEntity>) = dao.insertNotifications(notifications)
    suspend fun markNotificationAsRead(id: Long) = dao.markNotificationAsRead(id)
    suspend fun markAllNotificationsAsRead() = dao.markAllNotificationsAsRead()
    suspend fun deleteNotification(id: Long) = dao.deleteNotification(id)
    suspend fun deleteAllNotifications() = dao.deleteAllNotifications()

    suspend fun getShopProfileOnce(): ShopProfileEntity? = dao.getShopProfileOnce()

    suspend fun saveShopProfile(profile: ShopProfileEntity) = dao.saveShopProfile(profile)

    suspend fun getAllProductsOnce(): List<ProductEntity> = dao.getAllProductsOnce()

    suspend fun insertProducts(products: List<ProductEntity>) = dao.insertProducts(products)

    suspend fun insertProduct(product: ProductEntity): Long = dao.insertProduct(product)

    suspend fun updateProduct(product: ProductEntity) = dao.updateProduct(product)

    suspend fun deleteProduct(productId: Long) = dao.deleteProductById(productId)

    suspend fun getAllCustomersOnce(): List<CustomerEntity> = dao.getAllCustomersOnce()

    suspend fun insertCustomers(customers: List<CustomerEntity>) = dao.insertCustomers(customers)

    suspend fun getAllSalesOnce(): List<SaleEntity> = dao.getAllSalesOnce()

    suspend fun getAllSaleItemsOnce(): List<SaleItemEntity> = dao.getAllSaleItemsOnce()

    suspend fun insertCustomer(customer: CustomerEntity): Long = dao.insertCustomer(customer)

    suspend fun updateCustomer(customer: CustomerEntity) = dao.updateCustomer(customer)

    suspend fun insertSupplier(supplier: SupplierEntity): Long = dao.insertSupplier(supplier)

    suspend fun updateSupplier(supplier: SupplierEntity) = dao.updateSupplier(supplier)

    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> = dao.getSaleItemsOnce(saleId)

    suspend fun addCashMovement(
        type: String,
        amount: Long,
        reason: String,
        paymentMethod: String = "Espèces",
        operatorName: String = "Gérant",
        relatedId: Long? = null
    ): Long {
        return dao.insertCashMovement(
            CashMovementEntity(
                type = type,
                amount = amount,
                reason = reason,
                paymentMethod = paymentMethod,
                operatorName = operatorName,
                relatedId = relatedId
            )
        )
    }

    suspend fun openCashSession(openingBalance: Long, openedBy: String): Long {
        val session = CashSessionEntity(
            openedAt = System.currentTimeMillis(),
            openingBalance = openingBalance,
            status = "OPEN",
            openedBy = openedBy
        )
        val id = dao.insertCashSession(session)
        if (openingBalance > 0) {
            addCashMovement(
                type = "CASH_IN",
                amount = openingBalance,
                reason = "Fond de caisse initial à l'ouverture",
                operatorName = openedBy,
                relatedId = id
            )
        }
        return id
    }

    suspend fun closeCashSession(
        session: CashSessionEntity,
        actualCountedBalance: Long,
        closedBy: String,
        closingNotes: String
    ) {
        val difference = actualCountedBalance - session.closingExpectedBalance
        val updated = session.copy(
            closedAt = System.currentTimeMillis(),
            closingActualBalance = actualCountedBalance,
            difference = difference,
            status = "CLOSED",
            closedBy = closedBy,
            closingNotes = closingNotes
        )
        dao.updateCashSession(updated)
    }

    /**
     * Complete sale execution: decreases stock, records customer purchase/debt,
     * logs cash movement if cash, saves SaleEntity and lines.
     */
    suspend fun recordSale(
        customerId: Long?,
        customerName: String,
        items: List<SaleItemEntity>,
        subtotal: Long,
        discountAmount: Long,
        taxAmount: Long,
        totalAmount: Long,
        paymentMethod: String,
        isCredit: Boolean,
        cashierName: String,
        notes: String
    ): SaleEntity {
        val totalCostPrice = items.sumOf { it.unitBuyPrice * it.quantity }
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val count = dao.getSalesCount() + 1
        val transactionNumber = String.format("%s-%04d", datePrefix, count)

        val paymentStatus = if (isCredit) "CRÉDIT" else "PAYÉ"
        val amountPaid = if (isCredit) 0L else totalAmount

        val sale = SaleEntity(
            transactionNumber = transactionNumber,
            timestamp = System.currentTimeMillis(),
            customerId = customerId,
            customerName = customerName,
            subtotal = subtotal,
            discountAmount = discountAmount,
            taxAmount = taxAmount,
            totalAmount = totalAmount,
            totalCostPrice = totalCostPrice,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            amountPaid = amountPaid,
            cashierName = cashierName,
            notes = notes
        )

        val saleId = dao.insertSale(sale)

        // Save items and decrease stock
        val itemsWithSaleId = items.map { item ->
            item.copy(saleId = saleId)
        }
        dao.insertSaleItems(itemsWithSaleId)

        for (item in items) {
            dao.decreaseStock(item.productId, item.quantity)
            val updatedProduct = dao.getProductById(item.productId)
            if (updatedProduct != null && updatedProduct.stockQuantity <= updatedProduct.minStockAlert) {
                dao.insertNotification(
                    NotificationEntity(
                        title = "Alerte Stock Faible",
                        message = "${updatedProduct.name} : plus que ${updatedProduct.stockQuantity} ${updatedProduct.unit} en réserve (seuil : ${updatedProduct.minStockAlert}).",
                        type = "STOCK_ALERT",
                        priority = if (updatedProduct.stockQuantity <= 0) "URGENT" else "HIGH",
                        actionRoute = "stock",
                        referenceId = updatedProduct.id
                    )
                )
            }
        }

        // Cash or credit handling
        if (paymentMethod == "Espèces" && !isCredit) {
            addCashMovement(
                type = "SALE",
                amount = totalAmount,
                reason = "Vente N° $transactionNumber ($customerName)",
                paymentMethod = "Espèces",
                operatorName = cashierName,
                relatedId = saleId
            )
        }

        if (customerId != null) {
            dao.recordCustomerPurchase(customerId, totalAmount)
            if (isCredit) {
                dao.addCustomerDebt(customerId, totalAmount)
                dao.insertNotification(
                    NotificationEntity(
                        title = "Nouveau Crédit Accordé",
                        message = "Créance de $totalAmount FCFA enregistrée pour $customerName.",
                        type = "DEBT_ALERT",
                        priority = "HIGH",
                        actionRoute = "customers",
                        referenceId = customerId
                    )
                )
            }
        }

        // Sale completed notification
        dao.insertNotification(
            NotificationEntity(
                title = "Vente N° $transactionNumber",
                message = "Montant : $totalAmount FCFA ($customerName) - $paymentMethod",
                type = "SALE_COMPLETED",
                priority = "NORMAL",
                actionRoute = "reports",
                referenceId = saleId
            )
        )

        return sale.copy(id = saleId)
    }

    /**
     * Cancels an existing sale:
     * - Restores stock quantities for all sold items
     * - Adjusts customer debt or total spending if customer was attached
     * - Logs compensating cash movement if payment was in cash
     * - Updates sale entity with cancellation audit trail (reason, timestamp, operator)
     * - Generates an in-app audit notification
     */
    suspend fun cancelSale(
        saleId: Long,
        reason: String,
        cancelledBy: String
    ): Result<SaleEntity> {
        val sale = dao.getSaleById(saleId) ?: return Result.failure(Exception("Vente introuvable"))
        if (sale.isCancelled) {
            return Result.failure(Exception("Cette vente est déjà annulée."))
        }

        val items = dao.getSaleItemsOnce(saleId)

        // 1. Restore product stock
        for (item in items) {
            dao.increaseStock(item.productId, item.quantity)
        }

        // 2. Adjust customer debt and total spent if linked
        if (sale.customerId != null) {
            if (sale.paymentStatus == "CRÉDIT") {
                dao.reduceCustomerDebt(sale.customerId, sale.totalAmount)
            }
            val customer = dao.getCustomerById(sale.customerId)
            if (customer != null) {
                val updatedSpent = maxOf(0L, customer.totalSpent - sale.totalAmount)
                val updatedCount = maxOf(0, customer.totalSalesCount - 1)
                dao.updateCustomer(customer.copy(totalSpent = updatedSpent, totalSalesCount = updatedCount))
            }
        }

        // 3. Compensate cash movement if cash was collected
        if (sale.paymentMethod == "Espèces" && sale.paymentStatus != "CRÉDIT") {
            addCashMovement(
                type = "EXPENSE",
                amount = sale.totalAmount,
                reason = "Annulation Vente N° ${sale.transactionNumber} ($reason)",
                paymentMethod = "Espèces",
                operatorName = cancelledBy,
                relatedId = sale.id
            )
        }

        // 4. Update sale record with cancellation audit log
        val updatedSale = sale.copy(
            isCancelled = true,
            paymentStatus = "ANNULÉ",
            cancelledAt = System.currentTimeMillis(),
            cancellationReason = reason,
            cancelledBy = cancelledBy
        )
        dao.updateSale(updatedSale)

        // 5. System audit notification
        dao.insertNotification(
            NotificationEntity(
                title = "Vente Annulée N° ${sale.transactionNumber}",
                message = "Vente de ${sale.totalAmount} FCFA annulée par $cancelledBy. Motif : $reason. Les articles ont été réintégrés au stock.",
                type = "SALE_CANCELLED",
                priority = "HIGH",
                actionRoute = "transactions",
                referenceId = sale.id
            )
        )

        return Result.success(updatedSale)
    }

    suspend fun recordCustomerDebtPayment(
        customer: CustomerEntity,
        amount: Long,
        paymentMethod: String,
        operatorName: String
    ) {
        dao.reduceCustomerDebt(customer.id, amount)
        addCashMovement(
            type = "DEBT_PAYMENT",
            amount = amount,
            reason = "Règlement de dette par ${customer.name}",
            paymentMethod = paymentMethod,
            operatorName = operatorName,
            relatedId = customer.id
        )
    }

    suspend fun recordPurchase(
        supplier: SupplierEntity,
        product: ProductEntity,
        quantity: Int,
        unitCost: Long,
        isPaidFromCash: Boolean,
        operatorName: String,
        notes: String
    ) {
        val totalCost = unitCost * quantity
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val invoiceNumber = "ACH-$datePrefix-${System.currentTimeMillis() % 10000}"

        val purchase = PurchaseEntity(
            invoiceNumber = invoiceNumber,
            timestamp = System.currentTimeMillis(),
            supplierId = supplier.id,
            supplierName = supplier.name,
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            unitCost = unitCost,
            totalCost = totalCost,
            isPaidFromCash = isPaidFromCash,
            notes = notes
        )
        val purchaseId = dao.insertPurchase(purchase)

        // Update product stock and optionally buy price
        dao.increaseStock(product.id, quantity)

        if (isPaidFromCash) {
            addCashMovement(
                type = "SUPPLIER_PAYMENT",
                amount = totalCost,
                reason = "Achat stock $quantity x ${product.name} chez ${supplier.name}",
                paymentMethod = "Espèces",
                operatorName = operatorName,
                relatedId = purchaseId
            )
        }
    }

    suspend fun initializeStore(
        name: String,
        slogan: String,
        phone: String,
        address: String,
        city: String,
        businessType: String,
        adminName: String = "Gérant",
        adminPhone: String = "",
        adminPin: String = "1234",
        preloadDemoData: Boolean = false
    ) {
        val effectiveAdminPhone = if (adminPhone.isNotBlank()) adminPhone else phone

        // Save local admin account and setup completion flag in DataStore
        dataStore.saveSetupWizardData(
            shopName = name,
            slogan = slogan,
            shopPhone = phone,
            address = address,
            city = city,
            businessType = businessType,
            adminName = adminName,
            adminPhone = effectiveAdminPhone,
            adminPin = adminPin,
            currency = "FCFA"
        )

        val profile = ShopProfileEntity(
            id = 1,
            name = name,
            slogan = slogan,
            phone = phone,
            address = address,
            city = city,
            currency = "FCFA",
            businessType = businessType,
            adminPin = adminPin,
            cashierPin = "0000",
            subscriptionTier = "Premium",
            isConfigured = true,
            receiptFooter = "Merci pour votre confiance ! À très bientôt !"
        )
        dao.saveShopProfile(profile)

        // Only populate presets if explicitly requested by user
        if (preloadDemoData) {
            val preset = BusinessTypePresets.getPreset(businessType)
            dao.insertProducts(preset.defaultProducts)

            for (c in BusinessTypePresets.getDefaultCustomers()) {
                dao.insertCustomer(c)
            }
            for (s in BusinessTypePresets.getDefaultSuppliers()) {
                dao.insertSupplier(s)
            }

            // Open initial cash session with a starter cash float of 50 000 FCFA
            openCashSession(openingBalance = 50000L, openedBy = adminName)
        }
    }

    /**
     * Purges all demo / test data from the application:
     * - Deletes all products
     * - Deletes all sales and sale items
     * - Deletes all customers and customer debt records
     * - Deletes all suppliers and supplier debt records
     * - Deletes all cash movements and active/closed cash sessions
     * - Deletes all purchase orders
     * - Deletes all test notifications
     * If keepShopProfile is false, also resets DataStore and marks profile as unconfigured.
     */
    suspend fun clearAllDemoData(keepShopProfile: Boolean = true) {
        dao.deleteAllProducts()
        dao.deleteAllSales()
        dao.deleteAllSaleItems()
        dao.deleteAllCustomers()
        dao.deleteAllSuppliers()
        dao.deleteAllCashMovements()
        dao.deleteAllCashSessions()
        dao.deleteAllPurchases()
        dao.deleteAllNotifications()

        if (!keepShopProfile) {
            dataStore.clearAll()
            val resetProfile = ShopProfileEntity(
                id = 1,
                name = "",
                slogan = "",
                phone = "",
                address = "",
                city = "",
                currency = "FCFA",
                businessType = "Commerce général",
                isConfigured = false
            )
            dao.saveShopProfile(resetProfile)
        }
    }

    fun getDao(): KonabDao = dao
}
