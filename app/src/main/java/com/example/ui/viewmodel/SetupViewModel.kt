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
    val shopName: String = "",
    val slogan: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "Ouagadougou",
    val currency: String = "FCFA",
    val selectedLogoIcon: String = "storefront",
    val selectedLogoColorHex: Long = 0xFF087A45,
    val selectedBusinessType: String = "Boutique",
    val adminName: String = "",
    val adminPhone: String = "",
    val adminPin: String = "",
    val confirmPin: String = "",
    val preloadDemoData: Boolean = false,
    val selectedSubscriptionTier: String = "Professionnel",
    val validationError: String? = null,
    val isCheckingName: Boolean = false,
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

    fun updateSubscriptionTier(tier: String) {
        _uiState.update { it.copy(selectedSubscriptionTier = tier) }
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

    fun validateBusinessInfoAndCheckUniqueness(onValid: () -> Unit) {
        if (!validateBusinessInfo()) return
        val state = _uiState.value
        if (_uiState.value.isCheckingName) return

        val app = getApplication<Application>()
        val activeAgent = com.example.data.remote.supabase.AgentDeploymentStorage.getSavedAgentSession(app)
        if (activeAgent == null) {
            _uiState.update {
                it.copy(validationError = "Veuillez d'abord créer ou connecter un compte Agent Déployeur.")
            }
            return
        }

        _uiState.update { it.copy(isCheckingName = true, validationError = null) }
        viewModelScope.launch {
            val existing = repository.getShopProfileOnce()
            if (existing != null && existing.isConfigured && existing.name.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        isCheckingName = false,
                        validationError = "La boutique « ${existing.name} » est déjà créée sur cet appareil et ne peut pas être recréée."
                    )
                }
                return@launch
            }

            val syncManager = com.example.data.remote.supabase.SupabaseSyncManager(app)
            val uniqueness = syncManager.verifyShopNameAvailable(state.shopName.trim())
            if (uniqueness.isFailure) {
                _uiState.update {
                    it.copy(
                        isCheckingName = false,
                        validationError = uniqueness.exceptionOrNull()?.message
                            ?: "Une boutique avec ce nom existe déjà. Chaque boutique doit avoir un nom unique."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isCheckingName = false, validationError = null) }
            onValid()
        }
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

    fun completeSetup(
        onSuccess: () -> Unit
    ) {
        completeSetup(tier = _uiState.value.selectedSubscriptionTier, onSuccess = onSuccess)
    }

    fun completeSetup(
        tier: String,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        if (state.isSubmitting || state.isCompleted) {
            _uiState.update {
                it.copy(validationError = "Cette boutique a déjà été créée et ne peut pas être recréée.")
            }
            return
        }

        val app = getApplication<Application>()
        val currentAgent = com.example.data.remote.supabase.AgentDeploymentStorage.getSavedAgentSession(app)
        if (currentAgent == null) {
            _uiState.update {
                it.copy(validationError = "Vous devez d'abord créer ou connecter un compte Agent Déployeur.")
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, validationError = null, selectedSubscriptionTier = tier) }

        viewModelScope.launch {
            // Guard 1: Une fois qu'une boutique est créée, on ne peut plus la recréer
            val existing = repository.getShopProfileOnce()
            if (existing != null && existing.isConfigured && existing.name.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        validationError = "La boutique « ${existing.name} » est déjà créée et ne peut plus être recréée."
                    )
                }
                return@launch
            }

            val syncManager = com.example.data.remote.supabase.SupabaseSyncManager(app)

            // Guard 2: Unicité absolue du nom de boutique (local + Supabase Cloud)
            val uniqueness = syncManager.verifyShopNameAvailable(state.shopName.trim())
            if (uniqueness.isFailure) {
                val err = uniqueness.exceptionOrNull()?.message
                    ?: "Une boutique portant ce nom existe déjà dans la base de données."
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        validationError = err,
                        isCloudSyncSuccess = false,
                        cloudSyncMessage = err
                    )
                }
                return@launch
            }

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

            val currentDeployerId = currentAgent.id.trim().uppercase()

            // 2. Initialize local Room database
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
                preloadDemoData = state.preloadDemoData,
                subscriptionTier = tier,
                deployerId = currentDeployerId
            )
            com.example.util.BiometricSecurityHelper.setShopConfiguredCache(app, true)

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
                subscriptionTier = tier,
                isConfigured = true,
                receiptFooter = "Merci pour votre confiance ! À très bientôt !",
                deployerId = currentDeployerId
            )
            val cloudId = syncManager.resetCloudShopId()
            var cloudSuccess = false
            var cloudMsg = ""

            try {
                val syncResult = syncManager.pushShopProfileDirectly(profile, isNewShop = true)
                if (syncResult.isSuccess) {
                    if (state.preloadDemoData) {
                        try {
                            syncManager.syncProductsOnly(repository)
                        } catch (_: Exception) {}
                    }
                    cloudSuccess = true
                    cloudMsg = "Boutique « ${profile.name} » créée et liée à l'agent ${currentAgent.name} ($currentDeployerId)."
                } else {
                    cloudMsg = syncResult.exceptionOrNull()?.message ?: "Enregistrée localement (sync Cloud en attente)"
                    android.util.Log.e("SetupViewModel", "Erreur envoi boutique vers Cloud: $cloudMsg")
                }
                com.example.data.remote.supabase.AgentDeploymentStorage.markCreatedShopOnDevice(
                    app,
                    profile.name,
                    currentDeployerId
                )
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
