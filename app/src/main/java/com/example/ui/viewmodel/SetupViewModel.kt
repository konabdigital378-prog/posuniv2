package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KonabDatabase
import com.example.data.local.datastore.KonabDataStore
import com.example.data.repository.KonabRepository
import com.example.ui.screens.SetupWizardStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val currentStep: SetupWizardStep = SetupWizardStep.WELCOME,
    val shopName: String = "Boutique Kaba",
    val slogan: String = "Mode - Chaussures - Accessoires",
    val phone: String = "65413799",
    val address: String = "Avenue de la Nation",
    val city: String = "Bobo-Dioulasso",
    val currency: String = "FCFA",
    val selectedLogoIcon: String = "storefront",
    val selectedLogoColorHex: Long = 0xFF087A45,
    val selectedBusinessType: String = "Boutique",
    val adminName: String = "Gérant",
    val adminPhone: String = "65413799",
    val adminPin: String = "1234",
    val confirmPin: String = "1234",
    val preloadDemoData: Boolean = false,
    val validationError: String? = null,
    val isSubmitting: Boolean = false,
    val isCompleted: Boolean = false,
    val cloudShopId: Long? = null,
    val isCloudSyncSuccess: Boolean = false,
    val cloudSyncMessage: String? = null
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = KonabDataStore(application)
    private val repository = KonabRepository(
        dao = KonabDatabase.getDatabase(application).konabDao(),
        dataStore = dataStore
    )

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val allSteps = SetupWizardStep.values()

    fun updateShopName(name: String) {
        _uiState.update { it.copy(shopName = name, validationError = null) }
    }

    fun updateSlogan(slogan: String) {
        _uiState.update { it.copy(slogan = slogan) }
    }

    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phone = phone, validationError = null) }
    }

    fun updateAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun updateCity(city: String) {
        _uiState.update { it.copy(city = city) }
    }

    fun updateCurrency(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun updateLogoIcon(iconName: String) {
        _uiState.update { it.copy(selectedLogoIcon = iconName) }
    }

    fun updateLogoColorHex(colorHex: Long) {
        _uiState.update { it.copy(selectedLogoColorHex = colorHex) }
    }

    fun updateBusinessType(businessType: String) {
        _uiState.update { it.copy(selectedBusinessType = businessType) }
    }

    fun updateAdminName(name: String) {
        _uiState.update { it.copy(adminName = name, validationError = null) }
    }

    fun updateAdminPhone(phone: String) {
        _uiState.update { it.copy(adminPhone = phone) }
    }

    fun updateAdminPin(pin: String) {
        if (pin.length <= 4) {
            _uiState.update { it.copy(adminPin = pin, validationError = null) }
        }
    }

    fun updateConfirmPin(pin: String) {
        if (pin.length <= 4) {
            _uiState.update { it.copy(confirmPin = pin, validationError = null) }
        }
    }

    fun updatePreloadDemoData(preload: Boolean) {
        _uiState.update { it.copy(preloadDemoData = preload) }
    }

    fun validateBusinessInfo(): Boolean {
        val state = _uiState.value
        if (state.shopName.isBlank()) {
            _uiState.update { it.copy(validationError = "Le nom du commerce est obligatoire") }
            return false
        }
        if (state.phone.isBlank()) {
            _uiState.update { it.copy(validationError = "Le téléphone est obligatoire") }
            return false
        }
        _uiState.update { it.copy(validationError = null) }
        return true
    }

    fun validateAdminAccount(): Boolean {
        val state = _uiState.value
        if (state.adminName.isBlank()) {
            _uiState.update { it.copy(validationError = "Veuillez entrer le nom du gérant") }
            return false
        }
        if (state.adminPin.length < 4) {
            _uiState.update { it.copy(validationError = "Le code PIN doit comporter 4 chiffres") }
            return false
        }
        if (state.adminPin != state.confirmPin) {
            _uiState.update { it.copy(validationError = "Les deux codes PIN ne correspondent pas") }
            return false
        }
        _uiState.update { it.copy(validationError = null) }
        return true
    }

    fun clearValidationError() {
        _uiState.update { it.copy(validationError = null) }
    }

    fun nextStep(onComplete: () -> Unit = {}): Boolean {
        val state = _uiState.value

        when (state.currentStep) {
            SetupWizardStep.WELCOME -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.BUSINESS_INFO) }
                return true
            }

            SetupWizardStep.BUSINESS_INFO -> {
                if (!validateBusinessInfo()) return false
                _uiState.update { it.copy(currentStep = SetupWizardStep.ACTIVITY_TYPE, validationError = null) }
                return true
            }

            SetupWizardStep.ACTIVITY_TYPE -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.CUSTOMIZATION) }
                return true
            }

            SetupWizardStep.CUSTOMIZATION -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.ADMIN_ACCOUNT) }
                return true
            }

            SetupWizardStep.ADMIN_ACCOUNT -> {
                if (!validateAdminAccount()) return false
                _uiState.update { it.copy(currentStep = SetupWizardStep.READY, validationError = null) }
                return true
            }

            SetupWizardStep.READY -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.REGISTER_SHOP, validationError = null) }
                return true
            }

            SetupWizardStep.REGISTER_SHOP -> {
                completeSetup(onComplete)
                return true
            }
        }
    }

    fun previousStep(): Boolean {
        val current = _uiState.value.currentStep
        val prevIndex = current.ordinal - 1
        if (prevIndex >= 0) {
            _uiState.update { it.copy(currentStep = allSteps[prevIndex], validationError = null) }
            return true
        }
        return false
    }

    fun goToStep(step: SetupWizardStep) {
        _uiState.update { it.copy(currentStep = step, validationError = null) }
    }

    fun completeSetup(onSuccess: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            // 1. Save to DataStore
            dataStore.saveSetupWizardData(
                shopName = state.shopName.trim(),
                slogan = state.slogan.trim(),
                shopPhone = state.phone.trim(),
                address = state.address.trim(),
                city = state.city.trim(),
                businessType = state.selectedBusinessType,
                adminName = state.adminName.trim(),
                adminPhone = state.adminPhone.trim().ifBlank { state.phone.trim() },
                adminPin = state.adminPin,
                currency = state.currency,
                logoIcon = state.selectedLogoIcon,
                logoColor = state.selectedLogoColorHex
            )

            // 2. Initialize Room Database with presets
            repository.initializeStore(
                name = state.shopName.trim(),
                slogan = state.slogan.trim(),
                phone = state.phone.trim(),
                address = state.address.trim(),
                city = state.city.trim(),
                businessType = state.selectedBusinessType,
                adminName = state.adminName.trim(),
                adminPhone = state.adminPhone.trim().ifBlank { state.phone.trim() },
                adminPin = state.adminPin,
                preloadDemoData = state.preloadDemoData
            )

            val profile = com.example.data.local.entity.ShopProfileEntity(
                id = 1,
                name = state.shopName.trim(),
                slogan = state.slogan.trim(),
                phone = state.phone.trim(),
                address = state.address.trim(),
                city = state.city.trim(),
                currency = state.currency,
                businessType = state.selectedBusinessType,
                adminPin = state.adminPin,
                cashierPin = "0000",
                subscriptionTier = "Premium",
                isConfigured = true,
                receiptFooter = "Merci pour votre confiance ! À très bientôt !"
            )
            val syncManager = com.example.data.remote.supabase.SupabaseSyncManager(getApplication())
            val cloudId = syncManager.getOrCreateCloudShopId()
            var cloudSuccess = false
            var cloudMsg = ""

            try {
                val syncResult = syncManager.pushShopProfileDirectly(profile, isNewShop = true)
                if (syncResult.isSuccess) {
                    cloudSuccess = true
                    cloudMsg = "Boutique \"${profile.name}\" enregistrée et synchronisée avec succès dans le Cloud."
                } else {
                    cloudMsg = syncResult.exceptionOrNull()?.message ?: "Erreur synchronisation Cloud"
                    android.util.Log.e("SetupViewModel", "Erreur envoi boutique vers Cloud: $cloudMsg")
                }
                // Also synchronize all store tables (products, customers, etc.) to the cloud
                try {
                    syncManager.syncFullStore(repository)
                } catch (e: Exception) {
                    android.util.Log.e("SetupViewModel", "Erreur syncFullStore initial: ${e.message}")
                }
            } catch (e: Exception) {
                cloudMsg = e.message ?: "Erreur de connexion"
                android.util.Log.e("SetupViewModel", "Exception envoi direct boutique vers Cloud: ${e.message}", e)
            }

            _uiState.update { 
                it.copy(
                    isSubmitting = false, 
                    isCompleted = true,
                    cloudShopId = cloudId,
                    isCloudSyncSuccess = cloudSuccess,
                    cloudSyncMessage = cloudMsg
                ) 
            }
            onSuccess()
        }
    }
}
