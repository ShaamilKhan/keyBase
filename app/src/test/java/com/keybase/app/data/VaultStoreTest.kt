package com.keybase.app.data

import com.keybase.app.crypto.VaultCrypto
import com.keybase.app.crypto.WrongCredentialsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val keyfile = ByteArray(32) { 1 }
    private val device = ByteArray(32) { 2 }
    private val password = "correct horse battery staple"
    private val nastyPassword = "p@ss\"word\\n\n\t✓😀 \u0000end"

    private fun store(name: String = "vault.kbv") = VaultStore(File(tmp.root, name))

    private fun populated(name: String = "vault.kbv"): VaultStore {
        val s = store(name)
        s.create(keyfile, password, device)
        s.addGroup("Work", null)
        val work = s.groups.single { it.name == "Work" }
        s.upsert(Entry(title = "GitHub", username = "me@example.com", password = nastyPassword, url = "https://github.com", groupId = work.id))
        s.upsert(Entry(title = "Bank", username = "acct-42", password = "Tr0ub4dor&3", notes = "line1\nline2 \"quoted\" ünï"))
        return s
    }

    private fun fileBytes(name: String = "vault.kbv") = File(tmp.root, name).readBytes()

    private fun contains(haystack: ByteArray, needle: String): Boolean {
        val n = needle.toByteArray(Charsets.UTF_8)
        outer@ for (i in 0..haystack.size - n.size) {
            for (j in n.indices) if (haystack[i + j] != n[j]) continue@outer
            return true
        }
        return false
    }

    // ---- what is on disk -------------------------------------------------------------------

    @Test fun nothingReadableIsWrittenToDisk() {
        populated()
        val raw = fileBytes()
        for (secret in listOf("GitHub", "Bank", "me@example.com", "acct-42", "Tr0ub4dor", "github.com", "Work", "line1", "quoted", "password")) {
            assertFalse("'$secret' is visible in the vault file", contains(raw, secret))
        }
    }

    @Test fun everySaveUsesFreshRandomnessSoTheFileChangesEvenWithoutEdits() {
        val s = populated()
        val before = fileBytes()
        s.save()
        val after = fileBytes()
        assertFalse(before.contentEquals(after))
        assertFalse(before.copyOfRange(49, 61).contentEquals(after.copyOfRange(49, 61)))
    }

    @Test fun savingLeavesNoTemporaryFileBehind() {
        populated()
        assertFalse(File(tmp.root, "vault.kbv.tmp").exists())
        assertTrue(File(tmp.root, "vault.kbv").exists())
    }

    // ---- create / lock / unlock ------------------------------------------------------------

    @Test fun dataSurvivesLockAndUnlockExactly() {
        val s = populated()
        val before = s.entries.toList()
        val groupsBefore = s.groups.toList()
        s.lock()
        assertFalse(s.isUnlocked)
        assertTrue(s.entries.isEmpty())
        assertTrue(s.groups.isEmpty())

        s.unlock(keyfile, password, device)
        assertTrue(s.isUnlocked)
        assertEquals(before, s.entries.toList())
        assertEquals(groupsBefore, s.groups.toList())
        assertEquals(nastyPassword, s.entries.first { it.title == "GitHub" }.password)
    }

    @Test fun aSecondStoreObjectCanOpenTheSameFile() {
        populated()
        val other = store()
        other.unlock(keyfile, password, device)
        assertEquals(2, other.entries.size)
    }

    @Test fun wrongPasswordKeyFileOrDeviceAreAllRejectedTheSameWay() {
        populated().lock()
        val s = store()
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, "wrong password", device) }
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, password.uppercase(), device) }
        assertThrows(WrongCredentialsException::class.java) { s.unlock(ByteArray(32) { 9 }, password, device) }
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, password, ByteArray(32) { 9 }) }
        assertFalse(s.isUnlocked)
        assertTrue(s.entries.isEmpty())
        s.unlock(keyfile, password, device) // and the right ones still work afterwards
        assertTrue(s.isUnlocked)
    }

    @Test fun aFailedUnlockAttemptDoesNotDisturbAnOpenSession() {
        val s = populated()
        assertTrue(s.isUnlocked)
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, "nope", device) }
        assertTrue(s.isUnlocked)
        assertEquals(2, s.entries.size)
        s.lock()
        assertFalse(s.isUnlocked)
        assertTrue(s.entries.isEmpty())
    }

    @Test fun copyingTheVaultToAnotherPhoneDoesNotOpenIt() {
        populated()
        val copied = File(tmp.newFolder("other-phone"), "vault.kbv")
        File(tmp.root, "vault.kbv").copyTo(copied)
        val s = VaultStore(copied)
        val otherDevice = ByteArray(32) { 77 }
        // Right key file and right password, wrong phone: still locked out.
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, password, otherDevice) }
    }

    @Test fun lockingClearsEverythingAndStaysLocked() {
        val s = populated()
        s.lock()
        s.lock() // idempotent
        assertFalse(s.isUnlocked)
        assertTrue(s.entries.isEmpty())
        s.save() // saving while locked must be a harmless no-op, not a crash or an empty overwrite
        s.unlock(keyfile, password, device)
        assertEquals(2, s.entries.size)
    }

    @Test fun anEmptyVaultRoundTrips() {
        val s = store()
        s.create(keyfile, password, device)
        s.lock()
        s.unlock(keyfile, password, device)
        assertTrue(s.entries.isEmpty())
        assertTrue(s.groups.isEmpty())
    }

    @Test fun aLargeVaultRoundTrips() {
        val s = store()
        s.create(keyfile, password, device)
        repeat(400) { i ->
            s.entries.add(Entry(title = "Site $i", username = "user$i", password = "pw-$i-${"x".repeat(i % 40)}", notes = "n".repeat(i)))
        }
        s.save()
        s.lock()
        s.unlock(keyfile, password, device)
        assertEquals(400, s.entries.size)
        assertEquals("pw-399-${"x".repeat(399 % 40)}", s.entries.first { it.title == "Site 399" }.password)
    }

    // ---- tampering and damage --------------------------------------------------------------

    @Test fun editingAnyPartOfTheFileIsDetected() {
        populated().lock()
        val original = fileBytes()
        // Sample positions across the header, nonce, ciphertext and tag.
        val positions = listOf(0, 3, 4, 8, 12, 16, 17, 30, 48, 49, 60, 61, 100, original.size / 2, original.size - 17, original.size - 1)
        for (p in positions) {
            val bad = original.copyOf().also { it[p] = (it[p].toInt() xor 0x40).toByte() }
            File(tmp.root, "vault.kbv").writeBytes(bad)
            val s = store()
            val failure = runCatching { s.unlock(keyfile, password, device) }.exceptionOrNull()
            assertNotNull("changing byte $p went unnoticed", failure)
            assertFalse(s.isUnlocked)
            assertTrue(s.entries.isEmpty())
        }
    }

    @Test fun truncatedOrEmptyFilesFailCleanly() {
        populated().lock()
        val original = fileBytes()
        for (len in listOf(0, 10, 48, 60, 76, original.size - 1)) {
            File(tmp.root, "vault.kbv").writeBytes(original.copyOf(len))
            val s = store()
            assertNotNull("length $len", runCatching { s.unlock(keyfile, password, device) }.exceptionOrNull())
            assertFalse(s.isUnlocked)
        }
    }

    @Test fun anOversizedFileIsRefusedWithoutReadingItAll() {
        val f = File(tmp.root, "vault.kbv")
        java.io.RandomAccessFile(f, "rw").use { it.setLength(VaultCrypto.MAX_FILE_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { store().unlock(keyfile, password, device) }
    }

    @Test fun aBackupFileCannotBeOpenedAsTheLiveVaultOrTheOtherWayAround() {
        val s = populated()
        val backup = s.exportPortable()
        File(tmp.root, "swapped.kbv").writeBytes(backup)
        assertThrows(IllegalArgumentException::class.java) { store("swapped.kbv").unlock(keyfile, password, device) }
        assertThrows(IllegalArgumentException::class.java) {
            store("fresh.kbv").create(keyfile, password, device, fileBytes())
        }
    }

    // ---- backup and restore ----------------------------------------------------------------

    @Test fun backupRestoresOnAnotherPhoneWithTheSameKeyFileAndPassword() {
        val a = populated()
        val backup = a.exportPortable()

        val b = store("phone-b.kbv")
        b.create(keyfile, password, ByteArray(32) { 55 }, backup)
        assertEquals(a.entries.toList(), b.entries.toList())
        assertEquals(a.groups.toList(), b.groups.toList())
        b.lock()
        b.unlock(keyfile, password, ByteArray(32) { 55 }) // and it is bound to the new phone now
    }

    @Test fun backupDoesNotContainReadableData() {
        val backup = populated().exportPortable()
        for (secret in listOf("GitHub", "me@example.com", "Tr0ub4dor", "Work")) assertFalse(contains(backup, secret))
    }

    @Test fun backupRejectsWrongPasswordOrKeyFile() {
        val backup = populated().exportPortable()
        assertThrows(WrongCredentialsException::class.java) { store("x.kbv").create(keyfile, "wrong", device, backup) }
        assertThrows(WrongCredentialsException::class.java) { store("y.kbv").create(ByteArray(32) { 8 }, password, device, backup) }
        assertFalse(File(tmp.root, "x.kbv").exists())
        assertFalse(File(tmp.root, "y.kbv").exists())
    }

    @Test fun backupWithFlippedBytesIsRejected() {
        val backup = populated().exportPortable()
        val bad = backup.copyOf().also { it[backup.size - 5] = (it[backup.size - 5].toInt() xor 1).toByte() }
        assertThrows(WrongCredentialsException::class.java) { store("z.kbv").create(keyfile, password, device, bad) }
    }

    // ---- master password -------------------------------------------------------------------

    @Test fun checkPasswordAcceptsOnlyTheRightPassword() {
        val s = populated()
        assertTrue(s.checkPassword(keyfile, password))
        assertFalse(s.checkPassword(keyfile, "wrong"))
        assertFalse(s.checkPassword(ByteArray(32) { 5 }, password))
        s.lock()
        assertFalse(s.checkPassword(keyfile, password)) // nothing to check against while locked
    }

    @Test fun changingThePasswordReplacesTheOldOneAndKeepsTheData() {
        val s = populated()
        val before = s.entries.toList()
        val saltBefore = fileBytes().copyOfRange(17, 49)

        s.changePassword(keyfile, "a brand new passphrase", device)
        assertFalse("salt was reused", saltBefore.contentEquals(fileBytes().copyOfRange(17, 49)))
        s.lock()
        assertThrows(WrongCredentialsException::class.java) { s.unlock(keyfile, password, device) }
        s.unlock(keyfile, "a brand new passphrase", device)
        assertEquals(before, s.entries.toList())
    }

    @Test fun keysPayloadRoundTripsAndRejectsGarbage() {
        val s = populated()
        val payload = s.keysPayload()
        assertEquals(64, payload.size)
        val before = s.entries.toList()
        s.lock()
        s.unlockWithKeys(payload)
        assertEquals(before, s.entries.toList())

        s.lock()
        assertThrows(IllegalArgumentException::class.java) { s.unlockWithKeys(ByteArray(10)) }
        assertThrows(WrongCredentialsException::class.java) { s.unlockWithKeys(ByteArray(64)) }
        assertFalse(s.isUnlocked)
    }

    @Test fun passwordChangeInvalidatesAnOldKeysPayload() {
        val s = populated()
        val oldPayload = s.keysPayload()
        s.changePassword(keyfile, "another new passphrase", device)
        s.lock()
        assertThrows(WrongCredentialsException::class.java) { s.unlockWithKeys(oldPayload) }
    }

    // ---- entries ---------------------------------------------------------------------------

    @Test fun changingAPasswordKeepsTheOldOneInHistoryNewestFirstCappedAtTen() {
        val s = store()
        s.create(keyfile, password, device)
        s.upsert(Entry(title = "Site", password = "pw0"))
        for (i in 1..15) s.upsert(s.entries.single().copy(password = "pw$i"))
        val e = s.entries.single()
        assertEquals("pw15", e.password)
        assertEquals(10, e.history.size)
        assertEquals("pw14", e.history.first().password)
        assertEquals("pw5", e.history.last().password)
    }

    @Test fun editingOtherFieldsDoesNotTouchHistoryOrPasswordDate() {
        val s = store()
        s.create(keyfile, password, device)
        s.upsert(Entry(title = "Site", password = "pw0"))
        val first = s.entries.single()
        s.upsert(first.copy(title = "Renamed", notes = "hello"))
        val e = s.entries.single()
        assertEquals("Renamed", e.title)
        assertTrue(e.history.isEmpty())
        assertEquals(first.pwChanged, e.pwChanged)
    }

    @Test fun historySurvivesLockAndUnlock() {
        val s = store()
        s.create(keyfile, password, device)
        s.upsert(Entry(title = "Site", password = "old"))
        s.upsert(s.entries.single().copy(password = "new"))
        s.lock()
        s.unlock(keyfile, password, device)
        assertEquals("old", s.entries.single().history.single().password)
    }

    @Test fun deletingAnEntryRemovesItForGood() {
        val s = populated()
        val id = s.entries.first { it.title == "Bank" }.id
        s.delete(id)
        s.lock()
        s.unlock(keyfile, password, device)
        assertNull(s.entries.firstOrNull { it.id == id })
        assertEquals(1, s.entries.size)
    }

    @Test fun favouriteFlagToggles() {
        val s = populated()
        val id = s.entries.first().id
        s.toggleFavorite(id)
        assertTrue(s.entries.first { it.id == id }.favorite)
        s.toggleFavorite(id)
        assertFalse(s.entries.first { it.id == id }.favorite)
        s.toggleFavorite("no-such-id") // ignored
    }

    @Test fun appIdAndFolderSurviveARoundTrip() {
        val s = store()
        s.create(keyfile, password, device)
        s.addGroup("G", null)
        val g = s.groups.single()
        s.upsert(Entry(title = "App", password = "x", appId = "com.example.app", groupId = g.id))
        s.lock()
        s.unlock(keyfile, password, device)
        val e = s.entries.single()
        assertEquals("com.example.app", e.appId)
        assertEquals(g.id, e.groupId)
    }

    @Test fun oversizedFieldsAreTrimmed() {
        val s = store()
        s.create(keyfile, password, device)
        s.upsert(Entry(title = "t".repeat(5000), notes = "n".repeat(100_000), password = "x"))
        val e = s.entries.single()
        assertEquals(200, e.title.length)
        assertEquals(20_000, e.notes.length)
    }

    // ---- folders ---------------------------------------------------------------------------

    @Test fun foldersNestRenameAndShowTheirPath() {
        val s = store()
        s.create(keyfile, password, device)
        s.addGroup("Work", null)
        val work = s.groups.single()
        s.addGroup("Banks", work.id)
        val banks = s.groups.first { it.name == "Banks" }
        assertEquals("Work / Banks", s.pathOf(banks.id))
        assertEquals("", s.pathOf(null))
        s.renameGroup(work.id, "Job")
        assertEquals("Job / Banks", s.pathOf(banks.id))
        s.addGroup("   ", null) // blank names are ignored
        s.renameGroup(work.id, "")
        assertEquals(2, s.groups.size)
        assertEquals("Job", s.groups.first { it.id == work.id }.name)
    }

    @Test fun deletingAFolderMovesItsContentsUpAndLosesNothing() {
        val s = store()
        s.create(keyfile, password, device)
        s.addGroup("Top", null)
        val top = s.groups.single()
        s.addGroup("Middle", top.id)
        val mid = s.groups.first { it.name == "Middle" }
        s.addGroup("Leaf", mid.id)
        val leaf = s.groups.first { it.name == "Leaf" }
        s.upsert(Entry(title = "In middle", password = "a", groupId = mid.id))
        s.upsert(Entry(title = "In leaf", password = "b", groupId = leaf.id))

        s.deleteGroup(mid.id)

        assertNull(s.groups.firstOrNull { it.id == mid.id })
        assertEquals(top.id, s.groups.first { it.id == leaf.id }.parentId)
        assertEquals(top.id, s.entries.first { it.title == "In middle" }.groupId)
        assertEquals(leaf.id, s.entries.first { it.title == "In leaf" }.groupId)
        assertEquals(2, s.entries.size)

        s.deleteGroup(top.id) // deleting a root folder moves children to the top level
        assertNull(s.groups.first { it.id == leaf.id }.parentId)
        assertNull(s.entries.first { it.title == "In middle" }.groupId)
    }

    @Test fun deleteFileRemovesTheVaultAndLocks() {
        val s = populated()
        s.deleteFile()
        assertFalse(s.exists())
        assertFalse(s.isUnlocked)
        assertTrue(s.entries.isEmpty())
    }

    @Test fun snapshotIsACopyNotALiveView() {
        val s = populated()
        val snap = s.snapshot()
        s.lock()
        assertEquals(2, snap.size)
        assertTrue(s.snapshot().isEmpty())
        assertNotEquals(0, snap.first().password.length)
    }

    @Test fun twoVaultsWithTheSameCredentialsHaveDifferentSalts() {
        populated("a.kbv").lock()
        populated("b.kbv").lock()
        val saltA = fileBytes("a.kbv").copyOfRange(17, 49)
        val saltB = fileBytes("b.kbv").copyOfRange(17, 49)
        assertFalse(saltA.contentEquals(saltB))
        assertFalse(fileBytes("a.kbv").contentEquals(fileBytes("b.kbv")))
    }
}
