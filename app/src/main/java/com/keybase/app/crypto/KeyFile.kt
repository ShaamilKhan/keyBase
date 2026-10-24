package com.keybase.app.crypto

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.security.MessageDigest

class KeyFileException(message: String) : Exception(message)

object KeyFile {
    private const val MAX_BYTES = 64L * 1024 * 1024
    const val NEW_FILE_BYTES = 256

    /** SHA-256 of the file contents. The file itself is never stored or copied. */
    fun hash(cr: ContentResolver, uri: Uri): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val stream = try {
            cr.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: throw KeyFileException("Can't open the key file. Choose it again.")
        stream.use { s ->
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw KeyFileException("Key file is too large (max 64 MB).")
                md.update(buf, 0, n)
            }
        }
        if (total == 0L) throw KeyFileException("Key file is empty.")
        return md.digest()
    }

    fun displayName(cr: ContentResolver, uri: Uri): String? = runCatching {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Keeps read access to the file after a restart so it doesn't need to be picked each time. */
    fun remember(cr: ContentResolver, uri: Uri) {
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** True if Android still lets us read [uri] after a restart. */
    fun isRemembered(cr: ContentResolver, uri: Uri): Boolean =
        cr.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /** Reads a whole file into memory, refusing anything larger than [max] bytes. */
    fun readBounded(cr: ContentResolver, uri: Uri, max: Long): ByteArray {
        val stream = try { cr.openInputStream(uri) } catch (e: Exception) { null }
            ?: throw KeyFileException("Can't open that file.")
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        stream.use { s ->
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw KeyFileException("That file is too large to be a keyBase backup.")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    fun release(cr: ContentResolver, uriString: String?) {
        if (uriString == null) return
        runCatching { cr.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun writeNew(cr: ContentResolver, uri: Uri) {
        val bytes = VaultCrypto.randomBytes(NEW_FILE_BYTES)
        val out = cr.openOutputStream(uri, "wt") ?: throw KeyFileException("Can't write the key file.")
        out.use { it.write(bytes) }
        bytes.fill(0)
    }
}
