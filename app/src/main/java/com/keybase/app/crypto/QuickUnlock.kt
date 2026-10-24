package com.keybase.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional fingerprint-only unlock. The vault keys are encrypted with a hardware key that needs
 * a fresh strong-biometric check for every decryption, and that key is destroyed as soon as a
 * new fingerprint is enrolled. Off by default; expires after [MAX_AGE_MS].
 */
object QuickUnlock {
    private const val ALIAS = "kb_quick_aes_v1"
    private const val FILE = "quick.bin"
    private const val IV_LEN = 12
    const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // noBackupFilesDir is excluded from backups and device transfer.
    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, FILE)

    /** True while fingerprint unlock is set up and younger than [MAX_AGE_MS]; older setups are wiped. */
    fun isEnabled(ctx: Context): Boolean {
        val f = file(ctx)
        if (!f.exists()) return false
        if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) { wipe(ctx); return false }
        return runCatching { keystore().containsAlias(ALIAS) }.getOrDefault(false)
    }

    fun wipe(ctx: Context) {
        file(ctx).delete()
        runCatching { keystore().deleteEntry(ALIAS) }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUnlockedDeviceRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    /** Stores [payload] (the vault keys) behind a fingerprint check. */
    fun enable(
        activity: FragmentActivity,
        payload: ByteArray,
        onDone: () -> Unit,
        onFail: (Boolean, String) -> Unit,
    ) {
        val ctx = activity.applicationContext
        val cipher = try {
            runCatching { keystore().deleteEntry(ALIAS) }
            try { generate(true) } catch (e: Exception) { generate(false) }
            val key = keystore().getKey(ALIAS, null) as SecretKey
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: Exception) {
            onFail(false, "Couldn't set up fingerprint unlock (${e.javaClass.simpleName})")
            return
        }
        Biometrics.prompt(
            activity, "Turn on fingerprint unlock", null, BiometricPrompt.CryptoObject(cipher),
            onSuccess = { r ->
                try {
                    val c = r.cryptoObject!!.cipher!!
                    val ct = c.doFinal(payload)
                    file(ctx).writeBytes(c.iv + ct)
                    onDone()
                } catch (e: Exception) {
                    wipe(ctx)
                    onFail(false, "Couldn't save fingerprint unlock (${e.javaClass.simpleName})")
                }
            },
            onFail = { cancelled, msg -> wipe(ctx); onFail(cancelled, msg) },
        )
    }

    /** Fingerprint check, then the stored vault keys. [onFail] receives (userCancelled, message). */
    fun unlock(
        activity: FragmentActivity,
        onPayload: (ByteArray) -> Unit,
        onFail: (Boolean, String) -> Unit,
    ) {
        val ctx = activity.applicationContext
        val blob = runCatching { file(ctx).readBytes() }.getOrNull()
        if (blob == null || blob.size < IV_LEN + 16) { wipe(ctx); onFail(false, "Fingerprint unlock isn't set up"); return }
        val cipher = try {
            val key = keystore().getKey(ALIAS, null) as SecretKey
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, IV_LEN)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            wipe(ctx)
            onFail(false, "Your fingerprints changed. Unlock with your master password.")
            return
        } catch (e: Exception) {
            wipe(ctx)
            onFail(false, "Fingerprint unlock is no longer available. Use your master password.")
            return
        }
        Biometrics.prompt(
            activity, "Unlock keyBase", null, BiometricPrompt.CryptoObject(cipher),
            onSuccess = { r ->
                val out = runCatching { r.cryptoObject!!.cipher!!.doFinal(blob, IV_LEN, blob.size - IV_LEN) }.getOrNull()
                if (out != null) onPayload(out) else { wipe(ctx); onFail(false, "Fingerprint unlock failed. Use your master password.") }
            },
            onFail = onFail,
        )
    }
}
