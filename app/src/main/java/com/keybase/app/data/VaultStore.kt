package com.keybase.app.data

import androidx.compose.runtime.mutableStateListOf
import com.keybase.app.crypto.VaultCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Holds the decrypted vault in memory only while unlocked. Everything on disk is ciphertext. */
class VaultStore(private val file: File) {
    val entries = mutableStateListOf<Entry>()
    val groups = mutableStateListOf<Group>()
    private var key: ByteArray? = null
    private var baseKey: ByteArray? = null
    private var header: VaultCrypto.Header? = null

    private class VaultData(val entries: List<Entry>, val groups: List<Group>)

    @Volatile private var open = false
    val isUnlocked: Boolean get() = open

    fun exists() = file.exists()

    /** Copy of the logins for background readers such as autofill. */
    fun snapshot(): List<Entry> = entries.toList()

    private fun readFile(): ByteArray {
        require(file.length() <= VaultCrypto.MAX_FILE_BYTES) { "Vault file too large" }
        return file.readBytes()
    }

    // ---- create / unlock -------------------------------------------------------------------

    fun create(keyfileHash: ByteArray, password: String, deviceSecret: ByteArray, restoreFrom: ByteArray? = null) {
        val initial = restoreFrom?.let { openPortable(it, keyfileHash, password) } ?: VaultData(emptyList(), emptyList())
        val h = VaultCrypto.newHeader(VaultCrypto.FLAG_DEVICE_BOUND)
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        install(h, base, VaultCrypto.mixDevice(base, deviceSecret), initial)
        save()
    }

    fun unlock(keyfileHash: ByteArray, password: String, deviceSecret: ByteArray) {
        val blob = readFile()
        val h = VaultCrypto.parseHeader(blob)
        require(h.flags == VaultCrypto.FLAG_DEVICE_BOUND) { "Unexpected vault type" }
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        val k = VaultCrypto.mixDevice(base, deviceSecret)
        val data = try {
            openData(k, blob)
        } catch (e: Throwable) {
            base.fill(0); k.fill(0); throw e
        }
        install(h, base, k, data)
    }

    /** Fingerprint-only unlock: [payload] is what [keysPayload] returned earlier. */
    fun unlockWithKeys(payload: ByteArray) {
        require(payload.size == 64) { "Bad payload" }
        val blob = readFile()
        val h = VaultCrypto.parseHeader(blob)
        require(h.flags == VaultCrypto.FLAG_DEVICE_BOUND) { "Unexpected vault type" }
        val base = payload.copyOfRange(0, 32)
        val k = payload.copyOfRange(32, 64)
        val data = try {
            openData(k, blob)
        } catch (e: Throwable) {
            base.fill(0); k.fill(0); throw e
        }
        install(h, base, k, data)
    }

    fun keysPayload(): ByteArray = baseKey!! + key!!

    private fun openData(k: ByteArray, blob: ByteArray): VaultData {
        val plain = VaultCrypto.open(k, blob)
        try {
            return decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun install(h: VaultCrypto.Header, base: ByteArray, k: ByteArray, data: VaultData) {
        lock()
        header = h; baseKey = base; key = k
        groups.addAll(data.groups)
        entries.addAll(data.entries)
        open = true
    }

    @Synchronized
    fun lock() {
        open = false
        key?.fill(0); baseKey?.fill(0)
        key = null; baseKey = null; header = null
        entries.clear()
        groups.clear()
    }

    // ---- master password / backup ----------------------------------------------------------

    fun checkPassword(keyfileHash: ByteArray, password: String): Boolean {
        val h = header ?: return false
        val b = VaultCrypto.deriveBase(password, keyfileHash, h)
        val ok = MessageDigest.isEqual(b, baseKey)
        b.fill(0)
        return ok
    }

    /** Re-keys the vault with a fresh salt. The caller has already checked the current password. */
    fun changePassword(keyfileHash: ByteArray, newPassword: String, deviceSecret: ByteArray) {
        val h = VaultCrypto.newHeader(VaultCrypto.FLAG_DEVICE_BOUND)
        val base = VaultCrypto.deriveBase(newPassword, keyfileHash, h)
        val k = VaultCrypto.mixDevice(base, deviceSecret)
        try {
            writeFile(k, h) // only switch to the new keys once the file on disk uses them
        } catch (t: Throwable) {
            base.fill(0); k.fill(0); throw t
        }
        key?.fill(0); baseKey?.fill(0)
        header = h; baseKey = base; key = k
    }

    /** Encrypted copy that opens with key file + master password on any device. */
    fun exportPortable(): ByteArray {
        val h = header!!.withFlags(VaultCrypto.FLAG_PORTABLE)
        val plain = encode(entries.toList(), groups.toList())
        try {
            return VaultCrypto.seal(baseKey!!, h, plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun openPortable(blob: ByteArray, keyfileHash: ByteArray, password: String): VaultData {
        val h = VaultCrypto.parseHeader(blob)
        require(h.flags == VaultCrypto.FLAG_PORTABLE) { "Not a backup file" }
        val base = VaultCrypto.deriveBase(password, keyfileHash, h)
        try {
            return openData(base, blob)
        } finally {
            base.fill(0)
        }
    }

    // ---- persistence -----------------------------------------------------------------------

    fun save() {
        val k = key ?: return
        val h = header ?: return
        writeFile(k, h)
    }

    private fun writeFile(k: ByteArray, h: VaultCrypto.Header) {
        val plain = encode(entries.toList(), groups.toList())
        val blob = try {
            VaultCrypto.seal(k, h, plain)
        } finally {
            plain.fill(0)
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { it.write(blob); it.fd.sync() }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    // ---- entries ---------------------------------------------------------------------------

    fun deleteFile() {
        lock()
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    fun upsert(input: Entry) {
        // Caps keep a damaged or hostile input from bloating the vault file.
        val e = input.copy(
            title = input.title.take(200), username = input.username.take(300),
            password = input.password.take(1000), url = input.url.take(500),
            appId = input.appId.trim().take(200), notes = input.notes.take(20000),
        )
        val now = System.currentTimeMillis()
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) {
            val old = entries[i]
            val changed = old.password != e.password
            var hist = e.history
            if (changed && old.password.isNotEmpty()) {
                hist = (listOf(HistoryItem(old.password, now)) + hist).take(MAX_HISTORY)
            }
            entries[i] = e.copy(updated = now, pwChanged = if (changed) now else old.pwChanged, history = hist)
        } else {
            entries.add(e.copy(created = now, updated = now, pwChanged = now))
        }
        save()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    fun toggleFavorite(id: String) {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return
        entries[i] = entries[i].copy(favorite = !entries[i].favorite)
        save()
    }

    // ---- folders ---------------------------------------------------------------------------

    fun addGroup(name: String, parentId: String?) {
        val n = name.trim().take(60)
        if (n.isEmpty()) return
        groups.add(Group(name = n, parentId = parentId))
        save()
    }

    fun renameGroup(id: String, name: String) {
        val n = name.trim().take(60)
        val i = groups.indexOfFirst { it.id == id }
        if (n.isEmpty() || i < 0) return
        groups[i] = groups[i].copy(name = n)
        save()
    }

    /** Sub-folders and items move up to the parent of the deleted folder. */
    fun deleteGroup(id: String) {
        val g = groups.firstOrNull { it.id == id } ?: return
        for (i in groups.indices) if (groups[i].parentId == id) groups[i] = groups[i].copy(parentId = g.parentId)
        for (i in entries.indices) if (entries[i].groupId == id) entries[i] = entries[i].copy(groupId = g.parentId)
        groups.removeAll { it.id == id }
        save()
    }

    fun pathOf(groupId: String?): String {
        val names = ArrayList<String>()
        var cur = groupId
        var guard = 0
        while (cur != null && guard++ < 32) {
            val g = groups.firstOrNull { it.id == cur } ?: break
            names.add(0, g.name)
            cur = g.parentId
        }
        return names.joinToString(" / ")
    }

    // ---- serialisation ---------------------------------------------------------------------

    private fun JSONObject.strOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

    private fun encode(es: List<Entry>, gs: List<Group>): ByteArray {
        val ga = JSONArray()
        gs.forEach { g ->
            val o = JSONObject().put("id", g.id).put("name", g.name)
            if (g.parentId != null) o.put("parent", g.parentId)
            ga.put(o)
        }
        val ea = JSONArray()
        es.forEach { e ->
            val o = JSONObject()
                .put("id", e.id).put("title", e.title).put("user", e.username).put("pass", e.password)
                .put("url", e.url).put("app", e.appId).put("notes", e.notes).put("fav", e.favorite)
                .put("created", e.created).put("updated", e.updated).put("pwc", e.pwChanged)
            if (e.groupId != null) o.put("group", e.groupId)
            val ha = JSONArray()
            e.history.forEach { ha.put(JSONObject().put("p", it.password).put("t", it.replaced)) }
            o.put("hist", ha)
            ea.put(o)
        }
        return JSONObject().put("v", 2).put("groups", ga).put("entries", ea).toString().toByteArray(Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): VaultData {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val gs = ArrayList<Group>()
        root.optJSONArray("groups")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                gs.add(Group(o.getString("id"), o.optString("name", ""), o.strOrNull("parent")))
            }
        }
        val es = ArrayList<Entry>()
        root.optJSONArray("entries")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val hist = ArrayList<HistoryItem>()
                o.optJSONArray("hist")?.let { h ->
                    for (j in 0 until h.length()) {
                        val ho = h.getJSONObject(j)
                        hist.add(HistoryItem(ho.optString("p", ""), ho.optLong("t", 0L)))
                    }
                }
                val created = o.optLong("created", 0L)
                es.add(
                    Entry(
                        id = o.getString("id"),
                        groupId = o.strOrNull("group"),
                        title = o.optString("title", ""),
                        username = o.optString("user", ""),
                        password = o.optString("pass", ""),
                        url = o.optString("url", ""),
                        appId = o.optString("app", ""),
                        notes = o.optString("notes", ""),
                        favorite = o.optBoolean("fav", false),
                        created = created,
                        updated = o.optLong("updated", created),
                        pwChanged = o.optLong("pwc", created),
                        history = hist,
                    )
                )
            }
        }
        return VaultData(es, gs)
    }

    private companion object {
        const val MAX_HISTORY = 10
    }
}
