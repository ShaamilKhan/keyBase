package com.keybase.app.crypto

import android.content.Context
import com.keybase.app.data.SiteRules
import java.security.SecureRandom

data class GenOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
    val words: Int = 5,
    val passphrase: Boolean = false,
)

object PasswordTools {
    private val rng = SecureRandom()
    private const val AMBIGUOUS = "Il1O0o|"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

    @Volatile private var words: List<String>? = null

    private fun wordList(ctx: Context): List<String> {
        words?.let { return it }
        return synchronized(this) {
            words ?: ctx.assets.open("eff_short_wordlist_1.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { it.split('\t').getOrNull(1)?.trim()?.takeIf(String::isNotEmpty) }.toList()
            }.also { words = it }
        }
    }

    /** Random words (EFF short list, ~10.3 bits each) joined with dashes. */
    fun passphrase(ctx: Context, count: Int): String = passphraseFrom(wordList(ctx), count)

    fun passphraseFrom(list: List<String>, count: Int): String =
        (1..count.coerceIn(3, 12)).joinToString("-") { list[rng.nextInt(list.size)] }

    fun generate(ctx: Context, o: GenOptions, rules: SiteRules.Rules? = null): String =
        if (o.passphrase && rules == null) passphrase(ctx, o.words) else generateChars(o, rules)

    /** Random characters honouring [o] and, when given, a website's [rules] (the rules win). */
    fun generateChars(o: GenOptions, rules: SiteRules.Rules? = null): String {
        var pools: List<Set<Char>> = buildList {
            if (o.lower) add(SiteRules.LOWER)
            if (o.upper) add(SiteRules.UPPER)
            if (o.digits) add(SiteRules.DIGIT)
            if (o.symbols) add(SYMBOLS.toSet())
        }
        val required = ArrayList<Set<Char>>()
        var allChars: Set<Char>
        var len = o.length

        if (rules != null) {
            // The site's requirements win over the on-screen options.
            val allowed = rules.allowed
            val base = (if (allowed != null) allowed else pools.flatten().toSet()) + rules.required.flatten()
            required.addAll(rules.required)
            pools = if (allowed != null) {
                listOf(SiteRules.LOWER, SiteRules.UPPER, SiteRules.DIGIT, SYMBOLS.toSet()).map { it.intersect(base) }
            } else pools
            allChars = base
            len = len.coerceIn(rules.minLen.coerceAtLeast(1), rules.maxLen)
            if (len < required.size) len = required.size
            if (len > rules.maxLen) return "" // the site's own rules can't all be met
        } else {
            allChars = pools.flatten().toSet()
        }

        if (o.avoidAmbiguous) {
            val drop = AMBIGUOUS.toSet()
            pools = pools.map { it - drop }
            allChars = allChars - drop
            for (i in required.indices) required[i] = required[i] - drop
        }
        // Never generate spaces: sites trim them and they are easy to miss when typing.
        allChars = allChars - ' '
        pools = pools.map { it - ' ' }
        for (i in required.indices) required[i] = required[i] - ' '

        pools = pools.filter { it.isNotEmpty() }
        required.removeAll { it.isEmpty() }
        if (allChars.isEmpty()) return ""
        // Without a site rule capping the length, always leave room for one character of every class.
        if (rules == null) len = len.coerceAtLeast(pools.size)
        val all = allChars.toList()

        val maxCons = rules?.maxConsecutive ?: Int.MAX_VALUE
        repeat(200) {
            val chars = ArrayList<Char>(len)
            // One from every required set; with no site rules, one from each selected class.
            val mustHave = if (required.isNotEmpty()) required else pools
            mustHave.forEach { s -> if (chars.size < len) { val l = s.toList(); chars.add(l[rng.nextInt(l.size)]) } }
            while (chars.size < len) chars.add(all[rng.nextInt(all.size)])
            for (i in chars.indices.reversed()) { // Fisher-Yates
                val j = rng.nextInt(i + 1)
                val t = chars[i]; chars[i] = chars[j]; chars[j] = t
            }
            if (runOk(chars, maxCons)) return chars.joinToString("")
        }
        return ""
    }

    private fun runOk(c: List<Char>, max: Int): Boolean {
        if (max == Int.MAX_VALUE) return true
        var run = 1
        for (i in 1 until c.size) {
            run = if (c[i] == c[i - 1]) run + 1 else 1
            if (run > max) return false
        }
        return true
    }
}
