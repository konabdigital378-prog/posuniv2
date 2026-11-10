package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.KonabDao
import com.example.data.local.entity.*

@Database(
    entities = [
        ShopProfileEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CashMovementEntity::class,
        CashSessionEntity::class,
        PurchaseEntity::class,
        NotificationEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KonabDatabase : RoomDatabase() {

    abstract fun konabDao(): KonabDao

    companion object {
        @Volatile
        private var INSTANCE: KonabDatabase? = null

        fun getDatabase(context: Context): KonabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KonabDatabase::class.java,
                    "konab_pos_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
