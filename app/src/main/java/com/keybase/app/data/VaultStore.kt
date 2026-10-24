package com.keybase.app.data

import androidx.compose.runtime.mutableStateListOf
import com.keybase.app.crypto.VaultCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val updated: Long = System.currentTimeMillis(),
)

/** Holds the decrypted vault in memory only while unlocked; everything on disk is ciphertext. */
class VaultStore(private val file: File) {
    val entries = mutableStateListOf<Entry>()
    private var key: ByteArray? = null
    private var baseKey: ByteArray? = null
    private var header: VaultCrypto.Header? = null

    val isUnlocked get() = key != null
    fun exists() = file.exists()

    fun create(keyfileHash: ByteArray, password: String, deviceSecret: ByteArray, restoreFrom: ByteArray? = null) {
        val initial = restoreFrom?.let { openPortable(it, keyfileHash, password) } ?: emptyList()
        val h = VaultCrypto.newHeader(VaultCrypto.FLAG_DEVICE_BOUND)
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        install(h, base, VaultCrypto.mixDevice(base, deviceSecret), initial)
        save()
    }

    fun unlock(keyfileHash: ByteArray, password: String, deviceSecret: ByteArray) {
        val blob = file.readBytes()
        val h = VaultCrypto.parseHeader(blob)
        require(h.flags == VaultCrypto.FLAG_DEVICE_BOUND) { "Unexpected vault type" }
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        val k = VaultCrypto.mixDevice(base, deviceSecret)
        val plain = try {
            VaultCrypto.open(k, blob)
        } catch (e: Exception) {
            base.fill(0); k.fill(0); throw e
        }
        val list = decode(plain)
        plain.fill(0)
        install(h, base, k, list)
    }

    /** Backup that opens with key file + password on any device (no fingerprint binding). */
    fun exportPortable(): ByteArray {
        val h = header!!.withFlags(VaultCrypto.FLAG_PORTABLE)
        val plain = encode(entries)
        return VaultCrypto.seal(baseKey!!, h, plain).also { plain.fill(0) }
    }

    private fun openPortable(blob: ByteArray, keyfileHash: ByteArray, password: String): List<Entry> {
        val h = VaultCrypto.parseHeader(blob)
        require(h.flags == VaultCrypto.FLAG_PORTABLE) { "Not a backup file" }
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        return try {
            val plain = VaultCrypto.open(base, blob)
            decode(plain).also { plain.fill(0) }
        } finally {
            base.fill(0)
        }
    }

    private fun install(h: VaultCrypto.Header, base: ByteArray, k: ByteArray, list: List<Entry>) {
        lock()
        header = h; baseKey = base; key = k
        entries.addAll(list)
    }

    fun save() {
        val plain = encode(entries)
        val blob = VaultCrypto.seal(key!!, header!!, plain)
        plain.fill(0)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file)) { // renameTo can fail over an existing file on some systems
            file.delete(); tmp.renameTo(file)
        }
    }

    fun upsert(e: Entry) {
        val i = entries.indexOfFirst { it.id == e.id }
        val updated = e.copy(updated = System.currentTimeMillis())
        if (i >= 0) entries[i] = updated else entries.add(updated)
        save()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    fun lock() {
        key?.fill(0); baseKey?.fill(0)
        key = null; baseKey = null; header = null
        entries.clear()
    }

    private fun encode(list: List<Entry>): ByteArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("user", it.username)
                    .put("pass", it.password).put("url", it.url).put("notes", it.notes)
                    .put("updated", it.updated)
            )
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): List<Entry> {
        val arr = JSONArray(String(bytes, Charsets.UTF_8))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(
                o.getString("id"), o.getString("title"), o.getString("user"),
                o.getString("pass"), o.getString("url"), o.getString("notes"), o.getLong("updated"),
            )
        }
    }
}
