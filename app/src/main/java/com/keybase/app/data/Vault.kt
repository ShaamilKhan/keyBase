package com.keybase.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Process-wide vault, shared by the main screen and the autofill screens. Locking lives here and not in the
 * activity, so it still happens if Android destroys the screen while the app sits in the background.
 */
object Vault {
    @Volatile private var instance: VaultStore? = null
    @Volatile private var app: Context? = null
    private var receiverOn = false

    /** True while the main screen is on screen. */
    @Volatile var uiActive = false

    /** True only if the main screen itself unlocked the vault (not a fill that happened in another app). */
    @Volatile var uiSession = false

    /** SystemClock.elapsedRealtime() at which a vault left open in the background must be locked; 0 = none. */
    @Volatile private var deadline = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val lockRun = Runnable { if (!uiActive) app?.let { lockAll(it) } }

    fun store(ctx: Context): VaultStore {
        val a = ctx.applicationContext
        app = a
        ensureScreenOffReceiver(a)
        return instance ?: synchronized(this) {
            instance ?: VaultStore(File(a.filesDir, "vault.kbv")).also { instance = it }
        }
    }

    private fun ensureScreenOffReceiver(a: Context) {
        if (receiverOn) return
        synchronized(this) {
            if (receiverOn) return
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = lockAll(c)
            }
            ContextCompat.registerReceiver(a, r, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverOn = true
        }
    }

    /** Locks the vault, wipes our clipboard copy and cancels any pending timer. */
    fun lockAll(ctx: Context) {
        handler.removeCallbacks(lockRun)
        deadline = 0L
        uiSession = false
        instance?.lock()
        Clip.clearIfOurs(ctx)
    }

    /** The main screen left the foreground: the vault may stay open at most [ms] more. */
    fun lockSoon(ms: Long) {
        deadline = SystemClock.elapsedRealtime() + ms
        handler.removeCallbacks(lockRun)
        handler.postDelayed(lockRun, ms)
    }

    /** True if the vault was left in the background longer than allowed (checked on the real clock, not a timer). */
    fun backgroundExpired(): Boolean {
        val d = deadline
        return d != 0L && SystemClock.elapsedRealtime() >= d
    }

    /** The main screen is back in front: stop any pending background lock. */
    fun cancelTimers() {
        handler.removeCallbacks(lockRun)
        deadline = 0L
    }

    /**
     * True if the vault is unlocked and its time in the background has not run out. Handler timers stop while
     * the phone is in deep sleep, so every reader checks this clock too instead of trusting them.
     */
    fun isOpen(ctx: Context): Boolean {
        val s = store(ctx)
        if (!s.isUnlocked) return false
        val d = deadline
        if (!uiActive && d != 0L && SystemClock.elapsedRealtime() >= d) {
            lockAll(ctx)
            return false
        }
        return true
    }

    /** Locks right away when only a background fill had the vault open. */
    fun lockNowIfBackground() {
        if (!uiActive) app?.let { lockAll(it) }
    }
}
