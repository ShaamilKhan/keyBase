package com.keybase.app.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.security.MessageDigest

/**
 * The only place the app touches the clipboard. Copies are flagged sensitive (hidden from the
 * Android 13+ clipboard preview) and wiped after a delay and whenever the vault locks. Only
 * clips written here are wiped, so something you copied elsewhere is left alone.
 */
object Clip {
    private val handler = Handler(Looper.getMainLooper())
    private var clearRun: Runnable? = null
    @Volatile private var ours = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("kb_clip", Context.MODE_PRIVATE)

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun copy(ctx: Context, label: String, value: String, clearAfterSec: Int) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
        ours = true
        val ms = clearAfterSec.coerceIn(5, 300) * 1000L
        // Remembered on disk (as a hash, never the text) so a clear that Android's process kill interrupted
        // can still be finished the next time the app is in front.
        prefs(ctx).edit().putString("hash", fingerprint(value)).putLong("until", System.currentTimeMillis() + ms).apply()
        schedule(ctx.applicationContext, ms)
    }

    private fun schedule(app: Context, ms: Long) {
        clearRun?.let(handler::removeCallbacks)
        val r = Runnable { clearIfOurs(app) }
        clearRun = r
        handler.postDelayed(r, ms)
    }

    /** Call while the app has window focus (Android only lets the focused app read the clipboard). */
    fun sweep(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString("hash", null) == null) return
        val left = p.getLong("until", 0L) - System.currentTimeMillis()
        if (ours && left > 0) return // this process still has its own timer running
        if (left > 0) { schedule(ctx.applicationContext, left); return }
        clearIfOurs(ctx)
    }

    fun clearIfOurs(ctx: Context) {
        clearRun?.let(handler::removeCallbacks)
        clearRun = null
        val app = ctx.applicationContext
        val p = prefs(app)
        val hash = p.getString("hash", null)
        val until = p.getLong("until", 0L)
        val knownInProcess = ours
        ours = false
        if (!knownInProcess && hash == null) return

        val cm = app.getSystemService(ClipboardManager::class.java)
        if (knownInProcess) {
            // This process put the text there, so it can remove it; reading it back is not allowed in the background.
            runCatching { cm.clearPrimaryClip() }
            p.edit().clear().apply()
            return
        }
        // An earlier process copied it. Android only lets the app in front read the clipboard, so a clear tried from
        // the background can't compare yet: keep the record and let the next sweep (when we have focus) finish it.
        val text = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }.getOrNull()
        when {
            text != null && fingerprint(text) == hash -> { runCatching { cm.clearPrimaryClip() }; p.edit().clear().apply() }
            text != null -> p.edit().clear().apply() // the clipboard now holds something else; leave it alone
            System.currentTimeMillis() - until > STALE_MS -> p.edit().clear().apply() // gave up after a long time
        }
    }

    private const val STALE_MS = 10 * 60 * 1000L
}
