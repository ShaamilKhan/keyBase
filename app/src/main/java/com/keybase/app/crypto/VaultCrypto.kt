package com.keybase.app.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when the key file, master password or device key do not match (or the file was altered). */
class WrongCredentialsException : Exception("Wrong credentials")

/** The file was written by an earlier test build whose key scheme is no longer supported. */
class LegacyVaultException : Exception("Legacy vault format")

/**
 * Vault file layout:
 *   header (49 bytes): "KBV2" | flags | memKiB | iters | parallelism | salt(32)
 *   nonce  (12 bytes)
 *   AES-256-GCM ciphertext+tag (the whole header is bound as AAD)
 *
 * base key  = Argon2id( SHA-256( "kb-v1" | keyfileHash | len(pw) | NFKC(pw) ), salt )
 * vault key = base key                       (portable backup, flag 0)
 *           = HMAC(base key, deviceSecret)   (device-bound vault, flag 1)
 */
object VaultCrypto {
    const val FLAG_PORTABLE = 0
    const val FLAG_DEVICE_BOUND = 1

    const val MEM_KIB = 131072 // 128 MiB
    const val ITERS = 4
    const val PARALLELISM = 2

    // Accepted range when reading a header. Parameters are read before the file is
    // authenticated, so a tampered header must not be able to exhaust memory or time.
    private const val MIN_MEM = 32768
    private const val MAX_MEM = 262144
    private const val MAX_ITERS = 10
    private const val MAX_PAR = 4

    const val MAX_FILE_BYTES = 32L * 1024 * 1024
    private const val HEADER_LEN = 49
    private const val NONCE_LEN = 12
    private const val TAG_LEN = 16
    private val MAGIC = byteArrayOf(0x4B, 0x42, 0x56, 0x32) // "KBV2"
    private val LEGACY_MAGIC = byteArrayOf(0x4B, 0x42, 0x56, 0x31) // "KBV1"
    private val rng = SecureRandom()

    class Header(val flags: Int, val mem: Int, val iters: Int, val par: Int, val salt: ByteArray) {
        fun encode(): ByteArray =
            ByteBuffer.allocate(HEADER_LEN).put(MAGIC).put(flags.toByte())
                .putInt(mem).putInt(iters).putInt(par).put(salt).array()

        fun withFlags(f: Int) = Header(f, mem, iters, par, salt)
    }

    fun newHeader(flags: Int) = Header(flags, MEM_KIB, ITERS, PARALLELISM, randomBytes(32))

    fun parseHeader(blob: ByteArray): Header {
        require(blob.size > HEADER_LEN + NONCE_LEN + TAG_LEN) { "File too small" }
        val b = ByteBuffer.wrap(blob)
        val magic = ByteArray(4).also { b.get(it) }
        if (MessageDigest.isEqual(magic, LEGACY_MAGIC)) throw LegacyVaultException()
        require(MessageDigest.isEqual(magic, MAGIC)) { "Not a keyBase vault" }
        val flags = b.get().toInt()
        require(flags == FLAG_PORTABLE || flags == FLAG_DEVICE_BOUND) { "Bad flags" }
        val mem = b.getInt()
        val iters = b.getInt()
        val par = b.getInt()
        require(mem in MIN_MEM..MAX_MEM && iters in 1..MAX_ITERS && par in 1..MAX_PAR) { "Bad parameters" }
        val salt = ByteArray(32).also { b.get(it) }
        return Header(flags, mem, iters, par, salt)
    }

    fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun deriveBase(password: String, keyfileHash: ByteArray, h: Header): ByteArray {
        val pw = Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        val pre = sha256(
            "kb-v1".toByteArray() + keyfileHash +
                ByteBuffer.allocate(4).putInt(pw.size).array() + pw
        )
        pw.fill(0)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(h.salt)
            .withMemoryAsKB(h.mem)
            .withIterations(h.iters)
            .withParallelism(h.par)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(32)
        gen.generateBytes(pre, out)
        pre.fill(0)
        return out
    }

    fun mixDevice(base: ByteArray, deviceSecret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(base, "HmacSHA256"))
        return mac.doFinal(deviceSecret)
    }

    fun seal(key: ByteArray, header: Header, plaintext: ByteArray): ByteArray {
        val hb = header.encode()
        val nonce = randomBytes(NONCE_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(hb)
        return hb + nonce + c.doFinal(plaintext)
    }

    /** Throws [WrongCredentialsException] when the key is wrong or the file was modified. */
    fun open(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > HEADER_LEN + NONCE_LEN + TAG_LEN) { "File too small" }
        val hb = blob.copyOfRange(0, HEADER_LEN)
        val nonce = blob.copyOfRange(HEADER_LEN, HEADER_LEN + NONCE_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(hb)
        return try {
            c.doFinal(blob, HEADER_LEN + NONCE_LEN, blob.size - HEADER_LEN - NONCE_LEN)
        } catch (e: AEADBadTagException) {
            throw WrongCredentialsException()
        }
    }
}
