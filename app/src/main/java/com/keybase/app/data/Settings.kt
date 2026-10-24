package com.keybase.app.data

import android.content.Context

/** Non-secret preferences. Excluded from cloud backup and device transfer (see data_extraction_rules). */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("kb_settings", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }

    /** 0 = lock as soon as the app leaves the screen. */
    var autoLockSec: Int
        get() = p.getInt("autolock", 60)
        set(v) { p.edit().putInt("autolock", v).apply() }

    var allowCopy: Boolean
        get() = p.getBoolean("allow_copy", true)
        set(v) { p.edit().putBoolean("allow_copy", v).apply() }

    /** Seconds before a copied username or password is wiped from the clipboard. */
    var clipClearSec: Int
        get() = p.getInt("clip_sec", 20)
        set(v) { p.edit().putInt("clip_sec", v).apply() }

    var keyFileUri: String?
        get() = p.getString("key_uri", null)
        set(v) { p.edit().putString("key_uri", v).apply() }

    var keyFileName: String?
        get() = p.getString("key_name", null)
        set(v) { p.edit().putString("key_name", v).apply() }

    var lastBackup: Long
        get() = p.getLong("last_backup", 0L)
        set(v) { p.edit().putLong("last_backup", v).apply() }

    var failCount: Int
        get() = p.getInt("fails", 0)
        set(v) { p.edit().putInt("fails", v).apply() }

    var lockUntil: Long
        get() = p.getLong("lock_until", 0L)
        set(v) { p.edit().putLong("lock_until", v).apply() }
}
