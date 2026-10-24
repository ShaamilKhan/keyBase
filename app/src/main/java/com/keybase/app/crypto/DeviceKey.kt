package com.keybase.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Hardware-backed HMAC key that never leaves the secure chip. Every use needs a fresh strong
 * fingerprint check, and its output is mixed into the vault key. So the vault opens only on this
 * phone, only with your fingerprint, and only with the key file and master password as well.
 *
 * Enrolling an extra fingerprint does not invalidate the key (that would lock you out of your own
 * vault); removing the screen lock or resetting the phone does destroy it. Restore from a backup then.
 */
object DeviceKey {
    private const val ALIAS = "kb_device_hmac_v4"
    private val OLD_ALIASES = listOf("kb_device_hmac_v1", "kb_device_hmac_v2", "kb_device_hmac_v3")
    private val CONTEXT = "kb-device-v1".toByteArray()
    private const val MISSING = "The phone's hardware key is gone (screen lock removed or phone reset). Restore from a backup."

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun exists(): Boolean = runCatching { keystore().containsAlias(ALIAS) }.getOrDefault(false)

    /** Creates the key if needed. Refuses a software-only key. */
    fun ensure() {
        val ks = keystore()
        OLD_ALIASES.forEach { if (ks.containsAlias(it)) ks.deleteEntry(it) }
        if (ks.containsAlias(ALIAS)) return
        try {
            generate(strongBox = true)
        } catch (e: Exception) {
            // Missing StrongBox surfaces as StrongBoxUnavailableException or a ProviderException.
            generate(strongBox = false)
        }
        val key = ks.getKey(ALIAS, null) as SecretKey
        if (securityLevel(key) == KeyProperties.SECURITY_LEVEL_SOFTWARE) {
            ks.deleteEntry(ALIAS)
            throw HardwareKeyException("no hardware-backed key storage")
        }
    }

    fun delete() {
        val ks = keystore()
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        OLD_ALIASES.forEach { if (ks.containsAlias(it)) ks.deleteEntry(it) }
    }

    /** "StrongBox chip", "Secure hardware", or null if there is no key yet. */
    fun levelLabel(): String? = runCatching {
        val ks = keystore()
        if (!ks.containsAlias(ALIAS)) return null
        when (securityLevel(ks.getKey(ALIAS, null) as SecretKey)) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox chip"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Secure hardware"
            else -> "Software"
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
            .setInvalidatedByBiometricEnrollment(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    /**
     * Shows the fingerprint prompt and, on success, returns the phone-bound secret.
     * [onFail] receives (userCancelled, message).
     */
    fun deviceSecret(
        activity: FragmentActivity,
        title: String,
        onSecret: (ByteArray) -> Unit,
        onFail: (Boolean, String) -> Unit,
    ) {
        val mac = try {
            val key = keystore().getKey(ALIAS, null) as? SecretKey
            if (key == null) { onFail(false, MISSING); return }
            Mac.getInstance("HmacSHA256").apply { init(key) }
        } catch (e: Exception) {
            onFail(false, MISSING)
            return
        }
        Biometrics.prompt(
            activity, title, null, BiometricPrompt.CryptoObject(mac),
            onSuccess = { r ->
                val secret = runCatching { r.cryptoObject!!.mac!!.doFinal(CONTEXT) }.getOrNull()
                if (secret != null) onSecret(secret) else onFail(false, "Fingerprint check failed")
            },
            onFail = onFail,
        )
    }
}

class HardwareKeyException(message: String) : Exception(message)
