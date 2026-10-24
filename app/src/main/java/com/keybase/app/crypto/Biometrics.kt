package com.keybase.app.crypto

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Thin wrapper around BiometricPrompt. Strong (Class 3) biometrics only, never the device PIN. */
object Biometrics {
    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * @param crypto when set, the prompt unlocks that hardware-backed key operation.
     * @param onFail receives (userCancelled, message).
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        crypto: BiometricPrompt.CryptoObject?,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onFail: (Boolean, String) -> Unit,
    ) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess(result)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onFail(cancelled, errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
        if (crypto != null) p.authenticate(info, crypto) else p.authenticate(info)
    }
}
