package com.keybase.app.crypto

import java.security.SecureRandom
import kotlin.math.ln

data class GenOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
)

data class Strength(val bits: Double, val label: String, val fraction: Float)

object PasswordTools {
    private val rng = SecureRandom()
    private const val AMBIGUOUS = "Il1O0o"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
    private val COMMON = listOf(
        "password", "qwerty", "letmein", "welcome", "admin", "iloveyou", "monkey",
        "dragon", "football", "abc123", "123456", "111111", "login", "master",
    )
    private val SEQUENCES = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiopasdfghjklzxcvbnm")

    fun generate(o: GenOptions): String {
        val pools = buildList {
            if (o.lower) add("abcdefghijklmnopqrstuvwxyz")
            if (o.upper) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (o.digits) add("0123456789")
            if (o.symbols) add(SYMBOLS)
        }.map { p -> if (o.avoidAmbiguous) p.filter { it !in AMBIGUOUS } else p }
            .filter { it.isNotEmpty() }
        if (pools.isEmpty()) return ""
        val len = o.length.coerceAtLeast(pools.size)
        val all = pools.joinToString("")
        val chars = ArrayList<Char>(len)
        pools.forEach { chars.add(it[rng.nextInt(it.length)]) } // one of each class
        while (chars.size < len) chars.add(all[rng.nextInt(all.length)])
        for (i in chars.indices.reversed()) { // Fisher-Yates
            val j = rng.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return chars.joinToString("")
    }

    fun estimate(pw: String): Strength {
        if (pw.isEmpty()) return Strength(0.0, "Empty", 0f)
        var pool = 0
        if (pw.any { it in 'a'..'z' }) pool += 26
        if (pw.any { it in 'A'..'Z' }) pool += 26
        if (pw.any { it in '0'..'9' }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 33
        val unique = pw.toSet().size
        // Repeated characters add little; weigh them at 30%.
        val effLen = unique + (pw.length - unique) * 0.3
        var bits = effLen * ln(pool.coerceAtLeast(2).toDouble()) / ln(2.0)

        val lower = pw.lowercase()
        if (COMMON.any { lower.contains(it) }) bits = minOf(bits, 24.0)
        for (seq in SEQUENCES) {
            for (i in 0..seq.length - 4) {
                val s = seq.substring(i, i + 4)
                if (lower.contains(s) || lower.contains(s.reversed())) bits -= 6
            }
        }
        bits = bits.coerceAtLeast(0.0)
        val label = when {
            bits < 28 -> "Very weak"
            bits < 40 -> "Weak"
            bits < 60 -> "Fair"
            bits < 80 -> "Strong"
            else -> "Very strong"
        }
        return Strength(bits, label, (bits / 100.0).coerceIn(0.0, 1.0).toFloat())
    }
}
