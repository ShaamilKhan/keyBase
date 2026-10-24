package com.keybase.app.crypto

import com.keybase.app.data.SiteRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PasswordToolsTest {
    private val symbols = "!@#$%^&*()-_=+[]{};:,.?/".toSet()

    private fun kind(c: Char) = when {
        c in 'a'..'z' -> 'l'
        c in 'A'..'Z' -> 'u'
        c in '0'..'9' -> 'd'
        else -> 's'
    }

    // ---- plain character passwords ---------------------------------------------------------

    @Test fun lengthIsRespectedForEveryLengthInTheSliderRange() {
        for (n in 8..64) assertEquals(n, PasswordTools.generateChars(GenOptions(length = n)).length)
    }

    @Test fun everyEnabledCharacterClassAppearsEveryTime() {
        repeat(500) {
            val p = PasswordTools.generateChars(GenOptions(length = 8))
            val kinds = p.map(::kind).toSet()
            assertEquals("missing a class in $p", setOf('l', 'u', 'd', 's'), kinds)
        }
    }

    @Test fun disabledClassesNeverAppear() {
        repeat(300) {
            val p = PasswordTools.generateChars(GenOptions(length = 30, upper = false, symbols = false))
            assertTrue(p, p.all { kind(it) == 'l' || kind(it) == 'd' })
        }
        repeat(300) {
            val p = PasswordTools.generateChars(GenOptions(length = 30, lower = false, upper = false, digits = false))
            assertTrue(p, p.all { it in symbols })
        }
    }

    @Test fun lookAlikesAreLeftOutWhenAsked() {
        repeat(300) {
            val p = PasswordTools.generateChars(GenOptions(length = 40, avoidAmbiguous = true))
            assertTrue(p, p.none { it in "Il1O0o|" })
        }
    }

    @Test fun nothingSelectedGivesAnEmptyResultNotACrash() {
        assertEquals("", PasswordTools.generateChars(GenOptions(upper = false, lower = false, digits = false, symbols = false)))
    }

    @Test fun lengthShorterThanTheNumberOfClassesStillCoversEachClass() {
        val p = PasswordTools.generateChars(GenOptions(length = 2))
        assertEquals(4, p.length)
        assertEquals(setOf('l', 'u', 'd', 's'), p.map(::kind).toSet())
    }

    @Test fun outputIsRandomAndNotConstant() {
        val seen = HashSet<String>()
        repeat(500) { seen.add(PasswordTools.generateChars(GenOptions(length = 20))) }
        assertEquals(500, seen.size)
    }

    @Test fun everyCharacterOfTheAlphabetGetsUsed() {
        // A biased or broken picker would never produce some characters.
        val used = HashSet<Char>()
        repeat(4000) { used.addAll(PasswordTools.generateChars(GenOptions(length = 64)).toList()) }
        val expected = ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + symbols
        assertEquals(expected - used, emptySet<Char>())
    }

    @Test fun characterFrequenciesAreRoughlyEven() {
        // 20,000 draws over 10 digits: each digit should land near 2,000 (chi-squared sanity check).
        val counts = IntArray(10)
        var total = 0
        repeat(2000) {
            PasswordTools.generateChars(GenOptions(length = 64, upper = false, lower = false, symbols = false)).forEach {
                counts[it - '0']++; total++
            }
        }
        val expected = total / 10.0
        val chi = counts.sumOf { (it - expected) * (it - expected) / expected }
        // 9 degrees of freedom: 99.99% of fair draws fall under 33.7.
        assertTrue("chi-squared $chi suggests a biased generator", chi < 40.0)
    }

    // ---- passphrases -----------------------------------------------------------------------

    private val words = File("src/main/assets/eff_short_wordlist_1.txt").readLines()
        .filter { it.isNotBlank() }
        .map { it.split('\t')[1].trim() }

    @Test fun wordListIsThe1296WordEffShortList() {
        assertEquals(1296, words.size)
        assertEquals(1296, words.toSet().size)
        assertTrue(words.all { w -> w.isNotEmpty() && w.all { it in 'a'..'z' || it == '-' } })
    }

    @Test fun passphraseHasTheRequestedNumberOfWordsJoinedByDashes() {
        // A few list entries (like "drop-down") contain a dash themselves, so this counts at least n-1 joins.
        for (n in 3..12) {
            val p = PasswordTools.passphraseFrom(words, n)
            assertTrue(p, p.count { it == '-' } >= n - 1)
            assertTrue(p, p.split("-").all { it.isNotEmpty() })
        }
    }

    @Test fun passphraseWordCountIsKeptBetweenThreeAndTwelve() {
        val few = PasswordTools.passphraseFrom(words, 1)
        val many = PasswordTools.passphraseFrom(words, 50)
        assertTrue(few.count { it == '-' } >= 2)
        assertTrue(many.count { it == '-' } >= 11)
        assertTrue(many.length < 12 * 12) // not 50 words
    }

    @Test fun passphrasesDiffer() {
        val seen = HashSet<String>()
        repeat(300) { seen.add(PasswordTools.passphraseFrom(words, 5)) }
        assertEquals(300, seen.size)
    }

    // ---- site rules ------------------------------------------------------------------------

    @Test fun parsesATypicalRuleString() {
        val r = SiteRules.parse("minlength: 8; maxlength: 16; required: digit; required: upper,lower;")!!
        assertEquals(8, r.minLen)
        assertEquals(16, r.maxLen)
        assertEquals(2, r.required.size)
        assertEquals(SiteRules.DIGIT, r.required[0])
        assertEquals(SiteRules.UPPER + SiteRules.LOWER, r.required[1])
        assertEquals(Int.MAX_VALUE, r.maxConsecutive)
        assertNull(r.allowed)
    }

    @Test fun parsesCustomCharacterClassesIncludingTheTrickyBracketForms() {
        val r = SiteRules.parse("minlength: 8; maxlength: 8; max-consecutive: 3; required: digit; required: upper,lower,[#\$+./:=?@[^_|~]];")!!
        assertEquals(3, r.maxConsecutive)
        val custom = r.required[1]
        for (c in "#\$+./:=?@[^_|~") assertTrue("missing '$c'", c in custom)
        for (c in 'a'..'z') assertTrue(c in custom)
        // Apple's grammar: a "]]" at the end of a class means a literal ']' followed by the closing bracket.
        assertTrue(']' in custom)
        assertFalse(' ' in custom)
    }

    @Test fun aSingleClosingBracketJustEndsTheClass() {
        val r = SiteRules.parse("required: [abc];")!!
        assertEquals(setOf('a', 'b', 'c'), r.required.single())
    }

    @Test fun parsesAllowedListsAndIgnoresUnknownProperties() {
        val r = SiteRules.parse("allowed: lower, digit; minlength: 6; max-repeating: 2; frobnicate: yes;")!!
        assertEquals(SiteRules.LOWER + SiteRules.DIGIT, r.allowed)
        assertEquals(6, r.minLen)
    }

    @Test fun garbageAndContradictionsGiveNoRulesInsteadOfCrashing() {
        assertNull(SiteRules.parse(""))
        assertNull(SiteRules.parse("   ;;;  "))
        assertNull(SiteRules.parse("minlength: 20; maxlength: 8;"))
        assertNull(SiteRules.parse("%%% not rules at all"))
        SiteRules.parse("required: [unterminated")
        SiteRules.parse("minlength: ; maxlength: x")
        SiteRules.parse(":::;;;[[[]]]")
    }

    @Test fun hostMatchingIsExact() {
        assertEquals("github.com", SiteRules.hostOf("https://www.github.com/login"))
        assertEquals("github.com", SiteRules.hostOf("GitHub.com"))
        assertEquals("a.example.org", SiteRules.hostOf("http://a.example.org:8080/x?y=1"))
        assertNull(SiteRules.hostOf(""))
        assertNull(SiteRules.hostOf("   "))
    }

    @Test fun generatedPasswordsHonourACustomRule() {
        val r = SiteRules.parse("minlength: 10; maxlength: 12; required: lower; required: upper; required: digit; max-consecutive: 2;")!!
        repeat(300) {
            val p = PasswordTools.generateChars(GenOptions(length = 40), r)
            assertTrue(p, p.length in 10..12)
            assertTrue(p, p.any { it in 'a'..'z' } && p.any { it in 'A'..'Z' } && p.any { it in '0'..'9' })
            assertNoLongRuns(p, 2)
        }
    }

    @Test fun anUnsatisfiableRuleReturnsNothingInsteadOfABrokenPassword() {
        val r = SiteRules.Rules(
            minLen = 1, maxLen = 2,
            required = listOf(SiteRules.LOWER, SiteRules.UPPER, SiteRules.DIGIT),
            allowed = null, maxConsecutive = Int.MAX_VALUE,
        )
        assertEquals("", PasswordTools.generateChars(GenOptions(), r))
    }

    /** Every one of Apple's published per-site rules must parse and produce a password that satisfies it. */
    @Test fun everyPublishedSiteRuleProducesAPasswordThatSatisfiesIt() {
        val root = JSONObject(File("src/main/assets/password-rules.json").readText())
        var checked = 0
        val failures = ArrayList<String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val site = keys.next()
            val src = root.optJSONObject(site)?.optString("password-rules", "").orEmpty()
            if (src.isEmpty()) continue
            val rules = SiteRules.parse(src) ?: continue
            checked++
            val satisfiable = rules.required.size <= rules.maxLen && rules.maxLen >= 1
            repeat(25) {
                val p = PasswordTools.generateChars(GenOptions(length = 20), rules)
                if (!satisfiable) { if (p.isNotEmpty()) failures.add("$site: produced a password for an impossible rule"); return@repeat }
                val problem = violation(p, rules)
                if (problem != null) failures.add("$site [$src] -> '$p': $problem")
            }
        }
        assertTrue("only $checked site rules were checked", checked > 150)
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun theBundledRuleTableContainsAKnownSiteThatParses() {
        val root = JSONObject(File("src/main/assets/password-rules.json").readText())
        assertTrue(root.has("1800flowers.com"))
        assertNotNull(SiteRules.parse(root.getJSONObject("1800flowers.com").getString("password-rules")))
    }

    private fun assertNoLongRuns(p: String, max: Int) {
        var run = 1
        for (i in 1 until p.length) {
            run = if (p[i] == p[i - 1]) run + 1 else 1
            assertTrue("run of ${run} in $p", run <= max)
        }
    }

    private fun violation(p: String, r: SiteRules.Rules): String? {
        if (p.isEmpty()) return "empty result"
        if (p.length < r.minLen) return "too short"
        if (p.length > r.maxLen) return "too long"
        if (' ' in p) return "contains a space"
        for (set in r.required) if (p.none { it in set }) return "missing a required class"
        val allowed = r.allowed
        if (allowed != null) {
            val ok = allowed + r.required.flatten()
            val bad = p.firstOrNull { it !in ok }
            if (bad != null) return "uses disallowed '$bad'"
        }
        var run = 1
        for (i in 1 until p.length) {
            run = if (p[i] == p[i - 1]) run + 1 else 1
            if (run > r.maxConsecutive) return "too many repeats"
        }
        return null
    }
}
