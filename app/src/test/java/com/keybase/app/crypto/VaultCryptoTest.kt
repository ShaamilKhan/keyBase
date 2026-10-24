package com.keybase.app.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class VaultCryptoTest {
    // Cheapest parameters parseHeader still accepts, so most tests run fast.
    private fun cheap(flags: Int = VaultCrypto.FLAG_DEVICE_BOUND, salt: ByteArray = ByteArray(32) { it.toByte() }) =
        VaultCrypto.Header(flags, 32768, 1, 1, salt)

    private fun key(b: Int = 7) = ByteArray(32) { b.toByte() }
    private val message = "secret data ✓ ünïcode 😀".toByteArray(Charsets.UTF_8)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // Layout: magic 0-3, flags 4, mem 5-8, iters 9-12, parallelism 13-16, salt 17-48, nonce 49-60, ciphertext 61+
    private val nonceStart = 49
    private val nonceEnd = 61

    // ---- encryption ------------------------------------------------------------------------

    @Test fun sealThenOpenReturnsThePlaintext() {
        val blob = VaultCrypto.seal(key(), cheap(), message)
        assertArrayEquals(message, VaultCrypto.open(key(), blob))
    }

    @Test fun plaintextDoesNotAppearInTheSealedBlob() {
        val blob = VaultCrypto.seal(key(), cheap(), "needle-in-a-haystack".toByteArray())
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("needle-in-a-haystack"))
    }

    @Test fun wrongKeyIsRejected() {
        val blob = VaultCrypto.seal(key(1), cheap(), message)
        assertThrows(WrongCredentialsException::class.java) { VaultCrypto.open(key(2), blob) }
    }

    @Test fun changingAnySingleByteMakesOpenFail() {
        val blob = VaultCrypto.seal(key(), cheap(), "short".toByteArray())
        for (i in blob.indices) {
            val bad = blob.copyOf()
            bad[i] = (bad[i].toInt() xor 0x01).toByte()
            assertThrows("byte $i was accepted after being changed", WrongCredentialsException::class.java) {
                VaultCrypto.open(key(), bad)
            }
        }
    }

    @Test fun changingAnySingleBitOfTheTagOrCiphertextMakesOpenFail() {
        val blob = VaultCrypto.seal(key(), cheap(), message)
        for (i in nonceEnd until blob.size) for (bit in 0..7) {
            val bad = blob.copyOf()
            bad[i] = (bad[i].toInt() xor (1 shl bit)).toByte()
            assertThrows(WrongCredentialsException::class.java) { VaultCrypto.open(key(), bad) }
        }
    }

    @Test fun truncatedAndExtendedBlobsAreRejected() {
        val blob = VaultCrypto.seal(key(), cheap(), message)
        assertThrows(WrongCredentialsException::class.java) { VaultCrypto.open(key(), blob.copyOf(blob.size - 1)) }
        assertThrows(WrongCredentialsException::class.java) { VaultCrypto.open(key(), blob + byteArrayOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.open(key(), blob.copyOf(20)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.open(key(), ByteArray(0)) }
    }

    @Test fun headerIsAuthenticatedSoSwappingItFails() {
        val a = VaultCrypto.seal(key(), cheap(salt = ByteArray(32) { 1 }), message)
        val b = VaultCrypto.seal(key(), cheap(salt = ByteArray(32) { 2 }), message)
        // Header of b on the body of a.
        val spliced = b.copyOfRange(0, nonceStart) + a.copyOfRange(nonceStart, a.size)
        assertThrows(WrongCredentialsException::class.java) { VaultCrypto.open(key(), spliced) }
    }

    @Test fun everySealUsesAFreshNonceAndCiphertext() {
        val nonces = HashSet<String>()
        val first = VaultCrypto.seal(key(), cheap(), message)
        val second = VaultCrypto.seal(key(), cheap(), message)
        assertFalse(first.contentEquals(second))
        repeat(2000) {
            val blob = VaultCrypto.seal(key(), cheap(), message)
            nonces.add(hex(blob.copyOfRange(nonceStart, nonceEnd)))
        }
        assertEquals("a nonce was reused", 2000, nonces.size)
    }

    // ---- header parsing --------------------------------------------------------------------

    @Test fun validHeaderRoundTrips() {
        val h = VaultCrypto.newHeader(VaultCrypto.FLAG_DEVICE_BOUND)
        val parsed = VaultCrypto.parseHeader(VaultCrypto.seal(key(), h, message))
        assertEquals(VaultCrypto.FLAG_DEVICE_BOUND, parsed.flags)
        assertEquals(VaultCrypto.MEM_KIB, parsed.mem)
        assertEquals(VaultCrypto.ITERS, parsed.iters)
        assertEquals(VaultCrypto.PARALLELISM, parsed.par)
        assertArrayEquals(h.salt, parsed.salt)
    }

    @Test fun newHeadersGetDifferentRandomSalts() {
        val salts = HashSet<String>()
        repeat(200) { salts.add(hex(VaultCrypto.newHeader(1).salt)) }
        assertEquals(200, salts.size)
    }

    @Test fun headerWithAbsurdCostIsRejectedBeforeAnyKeyDerivation() {
        // A tampered file must not be able to make the phone allocate gigabytes or run for minutes.
        fun blobWith(mem: Int, iters: Int, par: Int) =
            VaultCrypto.seal(key(), VaultCrypto.Header(1, mem, iters, par, ByteArray(32)), message)
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(1 shl 30, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(Int.MAX_VALUE, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(1024, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(32768, 0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(32768, 1000, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(32768, -1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(32768, 1, 0)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(blobWith(32768, 1, 100)) }
        VaultCrypto.parseHeader(blobWith(32768, 1, 1)) // the lower bound is still accepted
    }

    @Test fun unknownFlagsAndWrongMagicAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.parseHeader(VaultCrypto.seal(key(), cheap(flags = 7), message))
        }
        val blob = VaultCrypto.seal(key(), cheap(), message)
        val wrong = blob.copyOf().also { it[0] = 'X'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(wrong) }
        assertThrows(IllegalArgumentException::class.java) { VaultCrypto.parseHeader(ByteArray(10)) }
    }

    @Test fun earlyTestBuildFilesAreRecognisedAsLegacy() {
        val blob = VaultCrypto.seal(key(), cheap(), message)
        blob[3] = '1'.code.toByte() // "KBV2" -> "KBV1"
        assertThrows(LegacyVaultException::class.java) { VaultCrypto.parseHeader(blob) }
    }

    // ---- key derivation --------------------------------------------------------------------

    @Test fun argon2idMatchesTheOfficialTestVector() {
        // RFC 9106 section 5.3 (Argon2id). Confirms the library really computes Argon2id.
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3).withMemoryAsKB(32).withParallelism(4)
            .withSalt(ByteArray(16) { 2 })
            .withSecret(ByteArray(8) { 3 })
            .withAdditional(ByteArray(12) { 4 })
            .build()
        val gen = Argon2BytesGenerator().also { it.init(params) }
        val out = ByteArray(32)
        gen.generateBytes(ByteArray(32) { 1 }, out)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", hex(out))
    }

    @Test fun deriveBaseMatchesAnIndependentComputationOfTheDocumentedScheme() {
        // Pins the key scheme: if this ever changes, every existing vault becomes unreadable.
        val h = cheap()
        val kf = ByteArray(32) { 9 }
        val pw = "pässword"
        val pwBytes = pw.toByteArray(Charsets.UTF_8)
        val pre = MessageDigest.getInstance("SHA-256").digest(
            "kb-v1".toByteArray() + kf + ByteBuffer.allocate(4).putInt(pwBytes.size).array() + pwBytes
        )
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(h.salt).withMemoryAsKB(h.mem).withIterations(h.iters).withParallelism(h.par).build()
        val expected = ByteArray(32)
        Argon2BytesGenerator().also { it.init(params) }.generateBytes(pre, expected)
        assertArrayEquals(expected, VaultCrypto.deriveBase(pw, kf, h))
    }

    @Test fun deriveBaseIsDeterministicAndSensitiveToEveryInput() {
        val h = cheap()
        val kf = ByteArray(32) { 1 }
        val base = VaultCrypto.deriveBase("password", kf, h)
        assertEquals(32, base.size)
        assertArrayEquals(base, VaultCrypto.deriveBase("password", kf, h))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("Password", kf, h)))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("password ", kf, h)))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("", kf, h)))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("password", ByteArray(32) { 2 }, h)))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("password", kf.copyOf().also { it[31] = 0 }, h)))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("password", kf, cheap(salt = ByteArray(32) { 99 }))))
        assertFalse(base.contentEquals(VaultCrypto.deriveBase("password", kf, VaultCrypto.Header(1, 32768, 2, 1, h.salt))))
    }

    @Test fun equivalentUnicodeSpellingsOfThePasswordGiveTheSameKey() {
        val h = cheap()
        val kf = ByteArray(32)
        // Composed vs decomposed é, and the "fi" ligature: different keyboards type these differently.
        assertArrayEquals(VaultCrypto.deriveBase("café", kf, h), VaultCrypto.deriveBase("café", kf, h))
        assertArrayEquals(VaultCrypto.deriveBase("ﬁsh", kf, h), VaultCrypto.deriveBase("fish", kf, h))
    }

    @Test fun deviceSecretChangesTheVaultKey() {
        val base = key(5)
        val a = VaultCrypto.mixDevice(base, ByteArray(32) { 1 })
        val b = VaultCrypto.mixDevice(base, ByteArray(32) { 2 })
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
        assertFalse(a.contentEquals(base))
        assertArrayEquals(a, VaultCrypto.mixDevice(base, ByteArray(32) { 1 }))
        val expected = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(base, "HmacSHA256")) }.doFinal(ByteArray(32) { 1 })
        assertArrayEquals(expected, a)
    }

    @Test fun sha256MatchesTheKnownAnswerForAbc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hex(VaultCrypto.sha256("abc".toByteArray())),
        )
    }

    @Test fun fullStrengthDerivationWorksAndIsSlowEnoughToHurtGuessing() {
        val h = VaultCrypto.newHeader(VaultCrypto.FLAG_DEVICE_BOUND)
        val t0 = System.nanoTime()
        val k = VaultCrypto.deriveBase("a password", ByteArray(32), h)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(32, k.size)
        // 128 MiB x 4 passes. Even a fast CI machine needs well over 50 ms; a cheap setup would not.
        assertTrue("derivation took only $ms ms", ms > 50)
    }
}
