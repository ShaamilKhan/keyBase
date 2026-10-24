package com.keybase.app.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * The only place the app touches the clipboard. Copies are flagged sensitive (hidden from the
 * Android 13+ clipboard preview) and wiped after a delay and whenever the vault locks. Only
 * clips written here are wiped, so something you copied elsewhere is left alone.
 */
object Clip {
    private val handler = Handler(Looper.getMainLooper())
    private var clearRun: Runnable? = null
    @Volatile private var ours = false

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
        clearRun?.let(handler::removeCallbacks)
        val app = ctx.applicationContext
        val r = Runnable { clearIfOurs(app) }
        clearRun = r
        handler.postDelayed(r, clearAfterSec.coerceIn(5, 300) * 1000L)
    }

    fun clearIfOurs(ctx: Context) {
        clearRun?.let(handler::removeCallbacks)
        clearRun = null
        if (!ours) return
        ours = false
        runCatching { ctx.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
    }
}
