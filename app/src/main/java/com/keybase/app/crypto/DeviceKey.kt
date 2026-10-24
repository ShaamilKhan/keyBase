package com.keybase.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import javax.crypto.SecretKeyFactory
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * A hardware-backed HMAC key that can only be used right after a successful
 * strong-biometric authentication (per use). Its output is mixed into the vault key,
 * so the vault file cannot be decrypted off this device or without the fingerprint.
 * The key is invalidated if new fingerprints are enrolled; use a backup to recover.
 */
object DeviceKey {
    private const val ALIAS = "kb_device_hmac_v1"
    private val CONTEXT = "kb-device-v1".toByteArray()

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun ensure() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) return
        try {
            generate(strongBox = true)
        } catch (e: Exception) {
            // StrongBox missing can surface as StrongBoxUnavailableException or a ProviderException.
            generate(strongBox = false)
        }
        // Refuse to continue if the OS silently gave us a software-only key.
        val key = ks.getKey(ALIAS, null) as SecretKey
        if (securityLevel(key) == KeyProperties.SECURITY_LEVEL_SOFTWARE) {
            ks.deleteEntry(ALIAS)
            throw IllegalStateException("No hardware-backed keystore on this device")
        }
    }

    fun delete() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }

    /** "StrongBox", "Secure hardware", or null when no key exists yet. */
    fun levelLabel(): String? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) return null
        when (securityLevel(ks.getKey(ALIAS, null) as SecretKey)) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Secure hardware"
            else -> "Software (insecure)"
        }
    }.getOrNull()

    private fun securityLevel(key: SecretKey): Int {
        val f = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        return (f.getKeySpec(key, KeyInfo::class.java) as KeyInfo).securityLevel
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setUnlockedDeviceRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    /** May throw KeyPermanentlyInvalidatedException. */
    fun newMac(): Mac {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(ALIAS, null) as SecretKey
        return Mac.getInstance("HmacSHA256").apply { init(key) }
    }

    /** Shows the fingerprint prompt and returns the device secret on success. */
    fun authenticate(activity: FragmentActivity, mac: Mac, onResult: (Result<ByteArray>) -> Unit) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(runCatching { result.cryptoObject!!.mac!!.doFinal(CONTEXT) })
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(Result.failure(IllegalStateException(errString.toString())))
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock keyBase")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
            .authenticate(info, BiometricPrompt.CryptoObject(mac))
    }
}
