package com.keybase.app.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.keybase.app.crypto.QuickUnlock
import com.keybase.app.data.Vault

/**
 * Strict autofill:
 *  - a vault that is unlocked offers matching logins; each fill still needs a fresh fingerprint;
 *  - a locked vault offers one "Unlock keyBase" row, but only when fingerprint unlock is turned on
 *    (the master password and key file are never asked for in a pop-up);
 *  - matching is exact (see [AutofillMatcher]); it never saves anything.
 */
class KeyBaseAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancel: CancellationSignal, callback: FillCallback) {
        val response = try {
            respond(request)
        } catch (e: Exception) {
            null
        }
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()

    private fun respond(request: FillRequest): FillResponse? {
        // Compatibility-mode browsers report an unreliable address, so they are never trusted.
        if ((request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0) return null
        val structure = request.fillContexts.lastOrNull()?.structure ?: return null
        val form = FormParser.parse(structure)
        val passId = form.passId ?: return null
        if (form.webDomain == null && form.pkg == packageName) return null // never fill our own screens

        val store = Vault.store(this)
        // isOpen also checks the background deadline on the real clock, which timers can miss in deep sleep.
        if (Vault.isOpen(this)) {
            val matches = store.snapshot()
                .filter { AutofillMatcher.matches(it, form.pkg, form.webDomain, form.webScheme) }
                .take(MAX_MATCHES)
            if (matches.isEmpty()) return null
            val b = FillResponse.Builder()
            matches.forEach { b.addDataset(FillBuilder.authDataset(this, it, passId, form.userId)) }
            return b.build()
        }

        // Locked: a fingerprint-only unlock is the only thing that can be offered from here.
        if (!QuickUnlock.isEnabled(this)) return null
        val intent = Intent(this, AutofillAuthActivity::class.java)
            .putExtra(AutofillAuthActivity.EXTRA_MODE, AutofillAuthActivity.MODE_UNLOCK)
            .putExtra(AutofillAuthActivity.EXTRA_PASS_ID, passId)
            .putExtra(AutofillAuthActivity.EXTRA_USER_ID, form.userId)
            .putExtra(AutofillAuthActivity.EXTRA_PKG, form.pkg)
            .putExtra(AutofillAuthActivity.EXTRA_DOMAIN, form.webDomain)
            .putExtra(AutofillAuthActivity.EXTRA_SCHEME, form.webScheme)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val ids = listOfNotNull(form.userId, passId).toTypedArray()
        return FillResponse.Builder()
            .setAuthentication(ids, pi.intentSender, FillBuilder.view(this, "Unlock keyBase"))
            .build()
    }

    private companion object {
        const val MAX_MATCHES = 5
    }
}
