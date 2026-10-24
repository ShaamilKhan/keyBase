package com.keybase.app.crypto

import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.Configuration
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import java.util.Locale
import kotlin.math.ln

/** Strength estimate in bits. level: 0 very weak .. 4 very strong. */
data class Strength(val bits: Double, val label: String, val level: Int)

/**
 * Entropy estimate. Uses nbvcxz (dictionary, keyboard-pattern, date and repeat aware, the same
 * library KeePassDX uses) and falls back to a simple character-pool estimate if it can't load.
 * Call from a background thread: the first call loads the dictionaries.
 */
object Entropy {
    private const val MAX_ANALYSED = 128

    @Volatile private var engine: Nbvcxz? = null
    @Volatile private var failed = false

    private fun engine(): Nbvcxz? {
        engine?.let { return it }
        if (failed) return null
        return synchronized(this) {
            engine ?: try {
                val cfg: Configuration = ConfigurationBuilder()
                    .setLocale(Locale.getDefault())
                    .setMinimumEntropy(80.0)
                    .createConfiguration()
                Nbvcxz(cfg).also { engine = it }
            } catch (t: Throwable) {
                failed = true
                null
            }
        }
    }

    fun estimate(pw: String): Strength {
        if (pw.isEmpty()) return Strength(0.0, "Empty", 0)
        // Very long inputs are scored on their first characters: that is a safe lower bound, and it
        // stops a pasted run of one letter from looking strong.
        val sample = pw.take(MAX_ANALYSED)
        val bits = runCatching { engine()?.estimate(sample)?.entropy }.getOrNull() ?: poolBits(sample)
        return rate(bits)
    }

    fun rate(bits: Double): Strength = when {
        bits < 28 -> Strength(bits, "Very weak", 0)
        bits < 40 -> Strength(bits, "Weak", 1)
        bits < 60 -> Strength(bits, "Fair", 2)
        bits < 80 -> Strength(bits, "Strong", 3)
        else -> Strength(bits, "Very strong", 4)
    }

    /** Upper bound that ignores patterns; only used when nbvcxz is unavailable. */
    private fun poolBits(pw: String): Double {
        var pool = 0
        if (pw.any { it in 'a'..'z' }) pool += 26
        if (pw.any { it in 'A'..'Z' }) pool += 26
        if (pw.any { it in '0'..'9' }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 33
        val unique = pw.toSet().size
        val effLen = unique + (pw.length - unique) * 0.3
        return effLen * ln(pool.coerceAtLeast(2).toDouble()) / ln(2.0)
    }
}
