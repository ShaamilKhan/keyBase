package com.keybase.app.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.fragment.app.FragmentActivity
import com.keybase.app.crypto.Biometrics
import com.keybase.app.crypto.QuickUnlock
import com.keybase.app.data.Vault

/**
 * Fingerprint gate for autofill. Either confirms one chosen login ([MODE_FILL]) or unlocks a
 * locked vault with the fingerprint and offers the matching logins ([MODE_UNLOCK]).
 */
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val passId = autofillId(EXTRA_PASS_ID)
        if (passId == null || !Biometrics.available(this)) {
            cancel()
            return
        }
        val userId = autofillId(EXTRA_USER_ID)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_FILL -> fillOne(passId, userId)
            MODE_UNLOCK -> unlockAndOffer(passId, userId)
            else -> cancel()
        }
    }

    private fun autofillId(key: String): AutofillId? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(key, AutofillId::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra<AutofillId>(key)

    private fun fillOne(passId: AutofillId, userId: AutofillId?) {
        val store = Vault.store(this)
        val wanted = intent.getStringExtra(EXTRA_ENTRY)
        val entry = if (store.isUnlocked) store.snapshot().firstOrNull { it.id == wanted } else null
        if (entry == null) {
            cancel()
            return
        }
        Biometrics.prompt(
            this, "Fill password", entry.title.ifBlank { null }, null,
            onSuccess = {
                finishWith(FillBuilder.valueDataset(this, entry, passId, userId))
                Vault.lockNowIfBackground() // one fingerprint, one password; the vault doesn't stay open
            },
            onFail = { _, _ -> cancel() },
        )
    }

    private fun unlockAndOffer(passId: AutofillId, userId: AutofillId?) {
        val pkg = intent.getStringExtra(EXTRA_PKG)
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val scheme = intent.getStringExtra(EXTRA_SCHEME)
        val store = Vault.store(this)
        QuickUnlock.unlock(
            this,
            onPayload = { payload ->
                val opened = try {
                    store.unlockWithKeys(payload)
                    true
                } catch (e: Exception) {
                    QuickUnlock.wipe(applicationContext)
                    false
                } finally {
                    payload.fill(0)
                }
                if (!opened) {
                    cancel()
                } else {
                    val matches = store.snapshot()
                        .filter { AutofillMatcher.matches(it, pkg, domain, scheme) }
                        .take(MAX_MATCHES)
                    when {
                        matches.isEmpty() -> {
                            Vault.lockNowIfBackground()
                            cancel()
                        }
                        matches.size == 1 -> {
                            // One match: the fingerprint just given covers it. Android still shows it as a
                            // one-row list that you tap to fill.
                            val b = FillResponse.Builder()
                            b.addDataset(FillBuilder.valueDataset(this, matches[0], passId, userId))
                            finishWith(b.build())
                            Vault.lockNowIfBackground()
                        }
                        else -> {
                            // Several matches: each one asks for a fingerprint again, so only the chosen
                            // password leaves the vault.
                            val b = FillResponse.Builder()
                            matches.forEach { b.addDataset(FillBuilder.authDataset(this, it, passId, userId)) }
                            finishWith(b.build())
                            Vault.lockSoon(LOCK_AFTER_PICK_MS)
                        }
                    }
                }
            },
            onFail = { _, _ -> cancel() },
        )
    }

    private fun finishWith(result: Parcelable) {
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_PASS_ID = "pass_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_SCHEME = "scheme"
        const val MODE_FILL = "fill"
        const val MODE_UNLOCK = "unlock"
        private const val MAX_MATCHES = 5
        private const val LOCK_AFTER_PICK_MS = 30_000L
    }
}
