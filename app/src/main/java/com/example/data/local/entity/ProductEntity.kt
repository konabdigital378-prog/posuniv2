package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val barcode: String = "",
    val buyPrice: Long = 0, // In FCFA
    val sellPrice: Long = 0, // In FCFA
    val stockQuantity: Int = 0,
    val minStockAlert: Int = 5,
    val unit: String = "pièce", // pièce, kg, litre, mètre, etc.
    val iconName: String = "default",
    val isAvailable: Boolean = true,
    val imageUri: String = "",
    val galleryImagesJson: String = "[]"
) {
    fun getGalleryList(): List<String> {
        val list = mutableListOf<String>()
        if (galleryImagesJson.isNotBlank() && galleryImagesJson != "[]") {
            try {
                val jsonArr = JSONArray(galleryImagesJson)
                for (i in 0 until jsonArr.length()) {
                    val str = jsonArr.optString(i)
                    if (str.isNotBlank()) list.add(str)
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty() && imageUri.isNotBlank()) {
            list.add(imageUri)
        }
        return list
    }
}
