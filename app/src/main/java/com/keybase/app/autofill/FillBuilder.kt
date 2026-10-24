package com.keybase.app.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.keybase.app.R
import com.keybase.app.data.Entry

/** Builds the autofill suggestions shown by the system. */
object FillBuilder {
    fun view(ctx: Context, text: String): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.autofill_item).apply { setTextViewText(R.id.label, text) }

    private fun label(e: Entry): String =
        listOf(e.title.ifBlank { e.url }, e.username).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Login" }

    /** A suggestion with no secret in it: tapping it asks for a fingerprint, and only then is the password released. */
    fun authDataset(ctx: Context, e: Entry, passId: AutofillId, userId: AutofillId?): Dataset {
        val intent = Intent(ctx, AutofillAuthActivity::class.java)
            .putExtra(AutofillAuthActivity.EXTRA_MODE, AutofillAuthActivity.MODE_FILL)
            .putExtra(AutofillAuthActivity.EXTRA_ENTRY, e.id)
            .putExtra(AutofillAuthActivity.EXTRA_PASS_ID, passId)
            .putExtra(AutofillAuthActivity.EXTRA_USER_ID, userId)
        // Mutable because the autofill framework adds extras to it; the target is an explicit class.
        val pi = PendingIntent.getActivity(
            ctx, e.id.hashCode(), intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val b = Dataset.Builder(view(ctx, label(e))).setAuthentication(pi.intentSender)
        b.setValue(passId, null)
        if (userId != null) b.setValue(userId, null)
        return b.build()
    }

    /** The finished fill, handed over only after the fingerprint check. */
    fun valueDataset(ctx: Context, e: Entry, passId: AutofillId, userId: AutofillId?): Dataset {
        val b = Dataset.Builder(view(ctx, label(e)))
        b.setValue(passId, AutofillValue.forText(e.password))
        if (userId != null && e.username.isNotEmpty()) b.setValue(userId, AutofillValue.forText(e.username))
        return b.build()
    }
}
