package com.keybase.app.data

import android.content.Context
import org.json.JSONObject
import java.net.URI

/**
 * Per-website password requirements from Apple's password-manager-resources (MIT licence), so a
 * generated password is accepted by the site. Grammar: https://github.com/apple/password-manager-resources
 */
object SiteRules {
    class Rules(
        val minLen: Int,
        val maxLen: Int,
        val required: List<Set<Char>>,
        val allowed: Set<Char>?,
        val maxConsecutive: Int,
    )

    val UPPER: Set<Char> = ('A'..'Z').toSet()
    val LOWER: Set<Char> = ('a'..'z').toSet()
    val DIGIT: Set<Char> = ('0'..'9').toSet()
    val SPECIAL: Set<Char> = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toSet()
    private val ASCII_PRINTABLE: Set<Char> = UPPER + LOWER + DIGIT + SPECIAL

    @Volatile private var table: Map<String, String>? = null

    private fun load(ctx: Context): Map<String, String> {
        table?.let { return it }
        return synchronized(this) {
            table ?: run {
                val m = HashMap<String, String>()
                runCatching {
                    val text = ctx.assets.open("password-rules.json").bufferedReader().use { it.readText() }
                    val root = JSONObject(text)
                    val it = root.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val r = root.optJSONObject(k)?.optString("password-rules", "") ?: ""
                        if (r.isNotEmpty()) m[k.lowercase()] = r
                    }
                }
                table = m
                m
            }
        }
    }

    fun hostOf(url: String): String? = runCatching {
        val u = url.trim()
        if (u.isEmpty()) return null
        URI(if ("://" in u) u else "https://$u").host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    /** The site name that matched and its rules, or null when the site has no published rules. */
    fun lookup(ctx: Context, url: String): Pair<String, Rules>? {
        val host = hostOf(url) ?: return null
        val t = load(ctx)
        var cand: String = host
        while (true) {
            t[cand]?.let { src -> parse(src)?.let { return cand to it } }
            val dot = cand.indexOf('.')
            if (dot < 0 || cand.indexOf('.', dot + 1) < 0) return null
            cand = cand.substring(dot + 1)
        }
    }

    fun parse(src: String): Rules? {
        var minLen = 0
        var maxLen = Int.MAX_VALUE
        var maxCons = Int.MAX_VALUE
        val required = ArrayList<Set<Char>>()
        var allowed: MutableSet<Char>? = null
        var anyAllowedUnrestricted = false
        var i = 0
        val n = src.length

        fun skipWs() { while (i < n && src[i].isWhitespace()) i++ }

        while (i < n) {
            while (i < n && (src[i].isWhitespace() || src[i] == ';')) i++
            if (i >= n) break
            val ns = i
            while (i < n && (src[i].isLetter() || src[i] == '-')) i++
            val name = src.substring(ns, i).lowercase()
            skipWs()
            if (i >= n || src[i] != ':') {
                while (i < n && src[i] != ';') i++
                continue
            }
            i++ // ':'
            skipWs()
            when (name) {
                "minlength", "maxlength", "max-consecutive", "max-repeating", "max-sequential" -> {
                    val s = i
                    while (i < n && src[i].isDigit()) i++
                    val v = src.substring(s, i).toIntOrNull()
                    if (v != null) when (name) {
                        "minlength" -> minLen = v
                        "maxlength" -> maxLen = v
                        "max-consecutive" -> maxCons = v
                    }
                    while (i < n && src[i] != ';') i++
                }
                "required", "allowed" -> {
                    val union = HashSet<Char>()
                    var unrestricted = false
                    while (i < n) {
                        skipWs()
                        if (i >= n) break
                        if (src[i] == '[') {
                            i++
                            val cls = HashSet<Char>()
                            val first = i
                            while (i < n) {
                                val c = src[i]
                                if (c == ']') break
                                if (c == '-' && i > first) { i++; continue }
                                if (c in ' '..'~') cls.add(c)
                                i++
                            }
                            if (i < n && src[i] == ']') {
                                // "]]" means a literal ']' followed by the closing bracket
                                if (i + 1 < n && src[i + 1] == ']') { cls.add(']'); i += 2 } else i++
                            }
                            union.addAll(cls)
                        } else {
                            val s = i
                            while (i < n && (src[i].isLetter() || src[i] == '-')) i++
                            when (src.substring(s, i).lowercase()) {
                                "upper" -> union.addAll(UPPER)
                                "lower" -> union.addAll(LOWER)
                                "digit" -> union.addAll(DIGIT)
                                "special" -> union.addAll(SPECIAL)
                                "ascii-printable" -> union.addAll(ASCII_PRINTABLE)
                                "unicode" -> unrestricted = true
                                else -> if (i == s) i++ // unknown character, skip it
                            }
                        }
                        skipWs()
                        if (i < n && src[i] == ',') { i++; continue }
                        break
                    }
                    while (i < n && src[i] != ';') i++
                    if (name == "required") {
                        if (union.isNotEmpty()) required.add(union)
                    } else if (unrestricted) {
                        anyAllowedUnrestricted = true
                    } else {
                        (allowed ?: HashSet<Char>().also { allowed = it }).addAll(union)
                    }
                }
                else -> while (i < n && src[i] != ';') i++
            }
        }
        if (minLen == 0 && maxLen == Int.MAX_VALUE && required.isEmpty() && allowed == null && maxCons == Int.MAX_VALUE) return null
        if (maxLen < maxOf(minLen, 1)) return null
        return Rules(minLen, maxLen, required, if (anyAllowedUnrestricted) null else allowed, maxCons)
    }

    fun describe(r: Rules): String {
        val parts = ArrayList<String>()
        when {
            r.minLen > 0 && r.maxLen != Int.MAX_VALUE -> parts.add("${r.minLen} to ${r.maxLen} characters")
            r.minLen > 0 -> parts.add("at least ${r.minLen} characters")
            r.maxLen != Int.MAX_VALUE -> parts.add("up to ${r.maxLen} characters")
        }
        val kinds = ArrayList<String>()
        for (s in r.required) {
            kinds.add(
                when {
                    s == LOWER -> "a lowercase letter"
                    s == UPPER -> "an uppercase letter"
                    s == DIGIT -> "a digit"
                    s == SPECIAL -> "a symbol"
                    s.containsAll(LOWER) && s.containsAll(UPPER) -> "a letter"
                    else -> "specific characters"
                }
            )
        }
        if (kinds.isNotEmpty()) parts.add("needs " + kinds.joinToString(", "))
        if (r.maxConsecutive != Int.MAX_VALUE) parts.add("no more than ${r.maxConsecutive} repeated in a row")
        return parts.joinToString("; ")
    }
}
