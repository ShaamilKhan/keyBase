package com.keybase.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.keybase.app.crypto.DeviceKey
import com.keybase.app.crypto.VaultCrypto
import com.keybase.app.data.Entry
import com.keybase.app.data.VaultStore
import com.keybase.app.ui.EntryScreen
import com.keybase.app.ui.KeyBaseTheme
import com.keybase.app.ui.LockScreen
import com.keybase.app.ui.VaultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CLIP_CLEAR_MS = 20_000L

class MainActivity : FragmentActivity() {
    private lateinit var store: VaultStore
    private val handler = Handler(Looper.getMainLooper())
    private var locked by mutableStateOf(true)
    private var holdLock = false // true while a system picker is open, so onStop doesn't lock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, screen recording or recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        applyPlatformHardening()
        enableEdgeToEdge()
        store =VaultStore(File(filesDir, "vault.kbv"))

        setContent {
            KeyBaseTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                ) { App() }
            }
        }
    }

    /** Newest protections first; each is skipped on OS versions that lack it (floor: Android 12). */
    private fun applyPlatformHardening() {
        // Android 12+: other apps' overlays are hidden while we are on screen (anti-tapjacking).
        window.setHideOverlayWindows(true)
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+: no screenshot of the recents card.
            setRecentsScreenshotEnabled(false)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+: marks content sensitive, so it is hidden from screen sharing,
            // notification-style captures and non-trusted assistants.
            window.decorView.setContentSensitivity(View.CONTENT_SENSITIVITY_SENSITIVE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!holdLock && !isChangingConfigurations) lockNow()
    }

    private fun lockNow() {
        store.lock()
        locked = true
        clearClipboard()
    }

    private fun copy(label: String, value: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        cm.setPrimaryClip(clip)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ clearClipboard() }, CLIP_CLEAR_MS)
        Toast.makeText(this, "$label copied · clears in 20s", Toast.LENGTH_SHORT).show()
    }

    private fun clearClipboard() {
        runCatching { getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
    }

    private fun keyfileHash(uri: Uri): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)!!.use { s ->
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest()
    }

    @Composable
    private fun App() {
        val scope = rememberCoroutineScope()
        var keyUri by remember { mutableStateOf<Uri?>(null) }
        var keyName by remember { mutableStateOf<String?>(null) }
        var backupUri by remember { mutableStateOf<Uri?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var editing by remember { mutableStateOf<Entry?>(null) }
        var editingIsNew by remember { mutableStateOf(false) }
        val firstRun = !store.exists()

        val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            holdLock = false
            if (uri != null) {
                keyUri = uri
                keyName = "Key file selected"
                error = null
            }
        }
        val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            holdLock = false
            backupUri = uri
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            holdLock = false
            if (uri != null) {
                val bytes = store.exportPortable()
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show()
            }
        }

        fun submit(pw: String) {
            val ku = keyUri ?: return
            error = null
            if (!DeviceKey.canAuthenticate(this)) {
                error = "Set up a fingerprint (strong biometric) in system settings first."
                return
            }
            val mac = try {
                if (firstRun) DeviceKey.ensure()
                DeviceKey.newMac()
            } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
                error = "Fingerprints changed, so the vault key was invalidated. Restore from backup."
                return
            } catch (e: Exception) {
                error = "Fingerprint key error (${e.javaClass.simpleName}: ${e.message?.take(100)})"
                return
            }
            DeviceKey.authenticate(this, mac) { r ->
                val secret = r.getOrNull()
                if (secret == null) { error = "Fingerprint not confirmed"; return@authenticate }
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        runCatching {
                            val kh = keyfileHash(ku)
                            if (firstRun) {
                                val restore = backupUri?.let { contentResolver.openInputStream(it)!!.use { s -> s.readBytes() } }
                                store.create(kh, pw, secret, restore)
                            } else store.unlock(kh, pw, secret)
                        }
                    }
                    busy = false
                    if (result.isSuccess) {
                        locked = false
                        keyUri = null; keyName = null; backupUri = null
                    } else {
                        // Wrong key file / password / tampering share one message; the error type
                        // is appended so real crashes (e.g. out of memory) aren't hidden.
                        val t = result.exceptionOrNull()
                        error = "Couldn't unlock. Check your key file and password. (${t?.javaClass?.simpleName}: ${t?.message?.take(80)})"
                    }
                }
            }
        }

        val e = editing
        when {
            locked -> LockScreen(
                firstRun = firstRun,
                keyfileName = keyName,
                busy = busy,
                error = error,
                onPickKeyfile = { holdLock = true; pickKey.launch(arrayOf("*/*")) },
                onSubmit = ::submit,
                onPickBackup = { holdLock = true; pickBackup.launch(arrayOf("*/*")) },
            )
            e != null -> EntryScreen(
                initial = e,
                isNew = editingIsNew,
                onSave = { store.upsert(it); editing = null },
                onDelete = { store.delete(e.id); editing = null },
                onCopy = ::copy,
                onBack = { editing = null },
            )
            else -> VaultScreen(
                entries = store.entries,
                onOpen = { editing = it; editingIsNew = false },
                onAdd = { editing = Entry(); editingIsNew = true },
                onLock = ::lockNow,
                onBackup = { holdLock = true; exportLauncher.launch("keybase-backup.kbv") },
            )
        }
    }
}
