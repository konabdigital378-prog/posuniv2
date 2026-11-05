package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.konabDataStore: DataStore<Preferences> by preferencesDataStore(name = "konab_admin_preferences")

data class LocalAdminAccount(
    val adminName: String = "Gérant",
    val adminPhone: String = "65413799",
    val adminPin: String = "1234",
    val role: String = "ADMIN",
    val isSetupCompleted: Boolean = false,
    val shopName: String = "Boutique Kaba",
    val slogan: String = "Mode - Chaussures - Accessoires",
    val address: String = "Bobo-Dioulasso, Burkina Faso",
    val city: String = "Bobo-Dioulasso",
    val businessType: String = "Boutique",
    val currency: String = "FCFA",
    val logoIcon: String = "storefront",
    val logoColor: Long = 0xFF087A45
)

class KonabDataStore(private val context: Context) {

    companion object {
        val KEY_IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        val KEY_ADMIN_NAME = stringPreferencesKey("admin_name")
        val KEY_ADMIN_PHONE = stringPreferencesKey("admin_phone")
        val KEY_ADMIN_PIN = stringPreferencesKey("admin_pin")
        val KEY_ADMIN_ROLE = stringPreferencesKey("admin_role")

        val KEY_SHOP_NAME = stringPreferencesKey("shop_name")
        val KEY_SHOP_SLOGAN = stringPreferencesKey("shop_slogan")
        val KEY_SHOP_ADDRESS = stringPreferencesKey("shop_address")
        val KEY_SHOP_CITY = stringPreferencesKey("shop_city")
        val KEY_BUSINESS_TYPE = stringPreferencesKey("business_type")
        val KEY_CURRENCY = stringPreferencesKey("currency")
        val KEY_LOGO_ICON = stringPreferencesKey("logo_icon")
        val KEY_LOGO_COLOR = longPreferencesKey("logo_color")
    }

    val adminAccountFlow: Flow<LocalAdminAccount> = context.konabDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            LocalAdminAccount(
                adminName = preferences[KEY_ADMIN_NAME] ?: "Gérant",
                adminPhone = preferences[KEY_ADMIN_PHONE] ?: "65413799",
                adminPin = preferences[KEY_ADMIN_PIN] ?: "1234",
                role = preferences[KEY_ADMIN_ROLE] ?: "ADMIN",
                isSetupCompleted = preferences[KEY_IS_SETUP_COMPLETED] ?: false,
                shopName = preferences[KEY_SHOP_NAME] ?: "Boutique Kaba",
                slogan = preferences[KEY_SHOP_SLOGAN] ?: "Mode - Chaussures - Accessoires",
                address = preferences[KEY_SHOP_ADDRESS] ?: "Bobo-Dioulasso, Burkina Faso",
                city = preferences[KEY_SHOP_CITY] ?: "Bobo-Dioulasso",
                businessType = preferences[KEY_BUSINESS_TYPE] ?: "Boutique",
                currency = preferences[KEY_CURRENCY] ?: "FCFA",
                logoIcon = preferences[KEY_LOGO_ICON] ?: "storefront",
                logoColor = preferences[KEY_LOGO_COLOR] ?: 0xFF087A45
            )
        }

    val isSetupCompletedFlow: Flow<Boolean> = context.konabDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_SETUP_COMPLETED] ?: false
        }

    suspend fun saveSetupWizardData(
        shopName: String,
        slogan: String,
        shopPhone: String,
        address: String,
        city: String,
        businessType: String,
        adminName: String,
        adminPhone: String,
        adminPin: String,
        currency: String = "FCFA",
        logoIcon: String = "storefront",
        logoColor: Long = 0xFF087A45
    ) {
        context.konabDataStore.edit { preferences ->
            preferences[KEY_IS_SETUP_COMPLETED] = true
            preferences[KEY_SHOP_NAME] = shopName
            preferences[KEY_SHOP_SLOGAN] = slogan
            preferences[KEY_SHOP_ADDRESS] = address
            preferences[KEY_SHOP_CITY] = city
            preferences[KEY_BUSINESS_TYPE] = businessType
            preferences[KEY_CURRENCY] = currency
            preferences[KEY_LOGO_ICON] = logoIcon
            preferences[KEY_LOGO_COLOR] = logoColor

            preferences[KEY_ADMIN_NAME] = adminName
            preferences[KEY_ADMIN_PHONE] = adminPhone
            preferences[KEY_ADMIN_PIN] = adminPin
            preferences[KEY_ADMIN_ROLE] = "ADMIN"
        }
    }

    suspend fun updateAdminPin(newPin: String) {
        context.konabDataStore.edit { preferences ->
            preferences[KEY_ADMIN_PIN] = newPin
        }
    }

    suspend fun resetSetup() {
        context.konabDataStore.edit { preferences ->
            preferences[KEY_IS_SETUP_COMPLETED] = false
        }
    }

    suspend fun clearAll() {
        context.konabDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
