package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KonabDao {

    // Shop Profile
    @Query("SELECT * FROM shop_profile WHERE id = 1 LIMIT 1")
    fun getShopProfile(): Flow<ShopProfileEntity?>

    @Query("SELECT * FROM shop_profile WHERE id = 1 LIMIT 1")
    suspend fun getShopProfileOnce(): ShopProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShopProfile(profile: ShopProfileEntity)

    // Products
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isAvailable = 1 ORDER BY name ASC")
    fun getAvailableProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stockQuantity = stockQuantity - :quantity WHERE id = :productId")
    suspend fun decreaseStock(productId: Long, quantity: Int)

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE id = :productId")
    suspend fun increaseStock(productId: Long, quantity: Int)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: Long)

    // Customers
    @Query("SELECT * FROM customers ORDER BY totalSpent DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY totalSpent DESC")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Query("SELECT * FROM customers WHERE debtAmount > 0 ORDER BY debtAmount DESC")
    fun getDebtorCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET debtAmount = debtAmount + :amount WHERE id = :customerId")
    suspend fun addCustomerDebt(customerId: Long, amount: Long)

    @Query("UPDATE customers SET debtAmount = MAX(0, debtAmount - :amount) WHERE id = :customerId")
    suspend fun reduceCustomerDebt(customerId: Long, amount: Long)

    @Query("UPDATE customers SET totalSpent = totalSpent + :amount, totalSalesCount = totalSalesCount + 1 WHERE id = :customerId")
    suspend fun recordCustomerPurchase(customerId: Long, amount: Long)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    // Suppliers
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliersOnce(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId LIMIT 1")
    suspend fun getSupplierById(supplierId: Long): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppliers(suppliers: List<SupplierEntity>)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)

    // Sales
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    suspend fun getAllSalesOnce(): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<SaleEntity>)

    @Query("SELECT * FROM sales ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getSalesByCustomer(customerId: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Long): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSalesCount(): Int

    // Sale Items
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsOnce(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items ORDER BY id DESC")
    fun getAllSaleItems(): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items ORDER BY id DESC")
    suspend fun getAllSaleItemsOnce(): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    // Cash Movements
    @Query("SELECT * FROM cash_movements ORDER BY timestamp DESC")
    fun getAllCashMovements(): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements ORDER BY timestamp DESC")
    suspend fun getAllCashMovementsOnce(): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getCashMovementsSince(startTime: Long): Flow<List<CashMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovements(movements: List<CashMovementEntity>)

    // Cash Session
    @Query("SELECT * FROM cash_sessions WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun getActiveCashSession(): Flow<CashSessionEntity?>

    @Query("SELECT * FROM cash_sessions WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getActiveCashSessionOnce(): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    fun getAllCashSessions(): Flow<List<CashSessionEntity>>

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    suspend fun getAllCashSessionsOnce(): List<CashSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashSession(session: CashSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashSessions(sessions: List<CashSessionEntity>)

    @Update
    suspend fun updateCashSession(session: CashSessionEntity)

    // Purchases
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    suspend fun getAllPurchasesOnce(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<PurchaseEntity>)

    // Data wipe / restore utility
    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllSaleItems()

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM suppliers")
    suspend fun deleteAllSuppliers()

    @Query("DELETE FROM cash_movements")
    suspend fun deleteAllCashMovements()

    @Query("DELETE FROM cash_sessions")
    suspend fun deleteAllCashSessions()

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()

    // App Notifications
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsOnce(): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadNotificationsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun deleteAllNotifications()
}
