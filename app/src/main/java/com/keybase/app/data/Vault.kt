package com.keybase.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/** Process-wide vault, shared by the main screen and the autofill screens. */
object Vault {
    @Volatile private var instance: VaultStore? = null

    /** True while the main screen is on screen. */
    @Volatile var uiActive = false

    private val handler = Handler(Looper.getMainLooper())
    private val lockRun = Runnable { if (!uiActive) instance?.lock() }

    fun store(ctx: Context): VaultStore =
        instance ?: synchronized(this) {
            instance ?: VaultStore(File(ctx.applicationContext.filesDir, "vault.kbv")).also { instance = it }
        }

    /** Locks after [ms] unless the main screen is open by then. */
    fun lockSoon(ms: Long) {
        handler.removeCallbacks(lockRun)
        handler.postDelayed(lockRun, ms)
    }

    /** Locks right away when only a background fill had the vault open. */
    fun lockNowIfBackground() {
        handler.removeCallbacks(lockRun)
        if (!uiActive) instance?.lock()
    }
}
