package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED
}

object BiometricSecurityHelper {
    private const val PREFS_NAME = "konab_security_prefs"
    private const val KEY_LAUNCH_LOCK_ENABLED = "launch_lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_unlock_enabled"
    private const val KEY_HAS_CONFIGURED_SHOP = "has_configured_shop_cache"

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLaunchLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LAUNCH_LOCK_ENABLED, true)
    }

    fun setLaunchLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LAUNCH_LOCK_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun setShopConfiguredCache(context: Context, configured: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAS_CONFIGURED_SHOP, configured).apply()
    }

    fun hasConfiguredShopCache(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_CONFIGURED_SHOP, false)
    }

    fun shouldLockOnLaunch(context: Context): Boolean {
        return isLaunchLockEnabled(context) && hasConfiguredShopCache(context)
    }

    fun checkBiometricAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.HARDWARE_UNAVAILABLE
        }
    }

    fun canUseBiometricUnlock(context: Context): Boolean {
        return isBiometricEnabled(context) && checkBiometricAvailability(context) == BiometricAvailability.AVAILABLE
    }

    private fun findFragmentActivity(context: Context): FragmentActivity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? FragmentActivity
    }

    fun authenticateWithBiometrics(
        context: Context,
        title: String = "Sécurité KONAB POS",
        subtitle: String = "Authentifiez-vous par empreinte digitale pour accéder à l'application",
        negativeButtonText: String = "Utiliser le code Gérant",
        onSuccess: () -> Unit,
        onFallbackPin: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("Activité biométrique indisponible.")
            return
        }

        if (checkBiometricAvailability(context) != BiometricAvailability.AVAILABLE) {
            onError("Aucune empreinte digitale configurée sur cet appareil.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> onFallbackPin()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Empreinte non reconnue. Réessayez ou saisissez le code Gérant.")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
