package com.keybase.app.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntropyTest {
    @Test fun emptyPasswordHasNoStrength() {
        val s = Entropy.estimate("")
        assertEquals(0.0, s.bits, 0.0)
        assertEquals(0, s.level)
    }

    @Test fun commonPasswordsRateAsVeryWeak() {
        for (p in listOf("password", "123456", "qwerty", "letmein", "iloveyou", "abc123")) {
            val s = Entropy.estimate(p)
            assertTrue("$p scored ${s.bits} bits", s.bits < 28)
            assertEquals("$p", 0, s.level)
        }
    }

    @Test fun keyboardWalksAndRepeatsAreNotMistakenForStrong() {
        for (p in listOf("qwertyuiop", "asdfghjkl", "1234567890", "aaaaaaaaaaaa", "abcabcabcabc")) {
            assertTrue("$p scored ${Entropy.estimate(p).bits} bits", Entropy.estimate(p).bits < 40)
        }
    }

    @Test fun aLongRandomPasswordRatesVeryStrong() {
        val p = PasswordTools.generateChars(GenOptions(length = 24))
        val s = Entropy.estimate(p)
        assertTrue("scored ${s.bits}", s.bits > 80)
        assertEquals(4, s.level)
    }

    @Test fun longerAlwaysScoresAtLeastAsHighForTheSameRandomStyle() {
        val short = Entropy.estimate(PasswordTools.generateChars(GenOptions(length = 10))).bits
        val long = Entropy.estimate(PasswordTools.generateChars(GenOptions(length = 30))).bits
        assertTrue("$short vs $long", long > short)
    }

    @Test fun labelsFollowTheDocumentedThresholds() {
        assertEquals("Very weak", Entropy.rate(10.0).label)
        assertEquals("Weak", Entropy.rate(30.0).label)
        assertEquals("Fair", Entropy.rate(50.0).label)
        assertEquals("Strong", Entropy.rate(70.0).label)
        assertEquals("Very strong", Entropy.rate(90.0).label)
        assertEquals(1, Entropy.rate(28.0).level)
        assertEquals(0, Entropy.rate(27.99).level)
    }

    @Test fun hugeInputsDoNotHangOrCrashAndARepeatedLetterStaysWeak() {
        val s = Entropy.estimate("x".repeat(100_000))
        assertTrue("scored ${s.bits}", s.bits < 40)
        Entropy.estimate("\u0000￿😀 \n\t")
    }

    @Test fun masterPasswordGateAcceptsAGoodPasswordAndRejectsAWeakOne() {
        assertEquals(60.0, Entropy.MIN_MASTER_BITS, 0.0)
        assertTrue(Entropy.estimate("password123").bits < Entropy.MIN_MASTER_BITS)
        assertTrue(Entropy.estimate("Summer2024!").bits < Entropy.MIN_MASTER_BITS)
        // A 24-character generated password clears the gate comfortably.
        assertTrue(Entropy.estimate(PasswordTools.generateChars(GenOptions(length = 24))).bits >= Entropy.MIN_MASTER_BITS)
    }
}
